package net.fabricmc.annotater.test.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.HttpStatus;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import net.fabricmc.annotater.test.AbstractApiTest;
import net.fabricmc.annotater.thirdparty.GithubAPI;

public class GithubOAuthTest extends AbstractApiTest {
	@Test
	void githubAuthReturnsUrl() throws Exception {
		Response response = client.get("/v1/auth/github");

		assertStatus(HttpStatus.OK, response);

		String body = response.body().string();
		assertNotNull(body);
		assertFalse(body.isEmpty());

		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		assertTrue(json.has("url"), "Response should contain 'url' field");

		String url = json.get("url").getAsString();
		assertNotNull(url);
		assertFalse(url.isEmpty());
		assertTrue(url.startsWith("https://github.com/login/oauth/authorize"), "URL should start with GitHub OAuth URL");
	}

	@Test
	void githubAuthUrlContainsRequiredParameters() throws Exception {
		Response response = client.get("/v1/auth/github");
		assertStatus(HttpStatus.OK, response);

		String body = response.body().string();
		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		String url = json.get("url").getAsString();

		assertTrue(url.contains("client_id="), "URL should contain client_id parameter");
		assertTrue(url.contains("redirect_uri="), "URL should contain redirect_uri parameter");
		assertTrue(url.contains("state="), "URL should contain state parameter");
		assertTrue(url.contains("scope="), "URL should contain scope parameter");
	}

	@Test
	void githubAuthUrlContainsReadUserScope() throws Exception {
		Response response = client.get("/v1/auth/github");

		assertStatus(HttpStatus.OK, response);

		String body = response.body().string();
		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		String url = json.get("url").getAsString();

		assertTrue(url.contains("read%3Auser") || url.contains("read:user"), "URL should contain read:user scope");
	}

	@Test
	void githubAuthGeneratesDifferentStates() throws Exception {
		Response response1 = client.get("/v1/auth/github");
		Response response2 = client.get("/v1/auth/github");

		assertStatus(HttpStatus.OK, response1);
		assertStatus(HttpStatus.OK, response2);

		String body1 = response1.body().string();
		String body2 = response2.body().string();

		JsonObject json1 = JsonParser.parseString(body1).getAsJsonObject();
		JsonObject json2 = JsonParser.parseString(body2).getAsJsonObject();

		String url1 = json1.get("url").getAsString();
		String url2 = json2.get("url").getAsString();

		String state1 = extractParameter(url1, "state");
		String state2 = extractParameter(url2, "state");

		assertNotNull(state1);
		assertNotNull(state2);
		assertNotEquals(state1, state2, "Each request should generate a unique state parameter");
	}

	@Test
	void githubAuthContentTypeIsJson() throws Exception {
		Response response = client.get("/v1/auth/github");

		assertStatus(HttpStatus.OK, response);

		String contentType = response.header("Content-Type");
		assertNotNull(contentType);
		assertTrue(contentType.contains("application/json"), "Content-Type should be application/json");
	}

	@Test
	void githubLandingSuccess() throws Exception {
		Response urlResponse = client.get("/v1/auth/github");
		assertStatus(HttpStatus.OK, urlResponse);
		String testCode = extractParameter(
				JsonParser.parseString(urlResponse.body().string()).getAsJsonObject().get("url").getAsString(),
				"state"
		);

		String testAccessToken = "gho_testAccessToken";
		GithubAPI.GithubUser testUser = new GithubAPI.GithubUser(123456L, "Test User", "testuser");

		Mockito.when(mockGithubApi.accessToken(anyString(), anyString(), eq(testCode), anyString()))
				.thenReturn(testAccessToken);
		Mockito.when(mockGithubApi.getUser(testAccessToken))
				.thenReturn(testUser);

		String state = generateValidState();

		Response response = client.get("/v1/auth/github/landing?code=" + testCode + "&state=" + state);

		assertStatus(HttpStatus.FOUND, response);

		String location = response.header("Location");
		assertNotNull(location);
		assertEquals(config.apiUrl(), location);

		String setCookie = response.header("Set-Cookie");
		assertNotNull(setCookie, "Should set refreshToken cookie");
		assertTrue(setCookie.contains("refreshToken="), "Cookie should be named refreshToken");
		assertTrue(setCookie.contains("HttpOnly"), "Cookie should be HttpOnly");
		assertTrue(setCookie.contains("Secure"), "Cookie should be Secure");
		assertTrue(setCookie.contains("SameSite=Strict"), "Cookie should be SameSite=Strict");
	}

	@Test
	void githubLandingMissingCode() {
		String state = generateValidState();

		Response response = client.get("/v1/auth/github/landing?state=" + state);

		assertStatus(HttpStatus.BAD_REQUEST, response);
	}

	@Test
	void githubLandingMissingState() {
		Response response = client.get("/v1/auth/github/landing?code=test_code");

		assertStatus(HttpStatus.BAD_REQUEST, response);
	}

	@Test
	void githubLandingMissingBothParameters() {
		Response response = client.get("/v1/auth/github/landing");

		assertStatus(HttpStatus.BAD_REQUEST, response);
	}

	@Test
	void githubLandingInvalidState() {
		Response response = client.get("/v1/auth/github/landing?code=test_code&state=invalid_state");

		assertStatus(HttpStatus.UNAUTHORIZED, response);
	}

	@Test
	void githubLandingExpiredState() {
		String expiredState = JWT.create()
				.withIssuer(config.jwt().issuer())
				.withExpiresAt(Instant.now().minus(Duration.ofMinutes(1)))
				.withJWTId(UUID.randomUUID().toString())
				.withClaim("plt", "github")
				.sign(config.jwt().algorithm());

		Response response = client.get("/v1/auth/github/landing?code=test_code&state=" + expiredState);

		assertStatus(HttpStatus.UNAUTHORIZED, response);
	}

	@Test
	void githubLandingWrongPlatformInState() {
		String wrongPlatformState = JWT.create()
				.withIssuer(config.jwt().issuer())
				.withExpiresAt(Instant.now().plus(Duration.ofMinutes(10)))
				.withJWTId(UUID.randomUUID().toString())
				.withClaim("plt", "discord")
				.sign(config.jwt().algorithm());

		Response response = client.get("/v1/auth/github/landing?code=test_code&state=" + wrongPlatformState);

		assertStatus(HttpStatus.UNAUTHORIZED, response);
	}

	@Test
	void githubLandingGithubApiAccessTokenFailure() throws Exception {
		Mockito.when(mockGithubApi.accessToken(anyString(), anyString(), anyString(), anyString()))
				.thenThrow(new IOException("GitHub API error"));

		String state = generateValidState();

		Response response = client.get("/v1/auth/github/landing?code=test_code&state=" + state);

		assertStatus(HttpStatus.UNAUTHORIZED, response);
	}

	@Test
	void githubLandingGithubApiGetUserFailure() throws Exception {
		String testCode = "test_code";
		String testAccessToken = "gho_testAccessToken";

		Mockito.when(mockGithubApi.accessToken(anyString(), anyString(), eq(testCode), anyString()))
				.thenReturn(testAccessToken);
		Mockito.when(mockGithubApi.getUser(testAccessToken))
				.thenThrow(new IOException("GitHub user API error"));

		String state = generateValidState();

		Response response = client.get("/v1/auth/github/landing?code=" + testCode + "&state=" + state);

		assertStatus(HttpStatus.INTERNAL_SERVER_ERROR, response);
	}

	@Test
	void githubLandingRefreshTokenCookiePath() throws Exception {
		String testCode = "test_oauth_code";
		String testAccessToken = "gho_token";
		GithubAPI.GithubUser testUser = new GithubAPI.GithubUser(999L, "Test", "test");

		Mockito.when(mockGithubApi.accessToken(anyString(), anyString(), eq(testCode), anyString()))
				.thenReturn(testAccessToken);
		Mockito.when(mockGithubApi.getUser(testAccessToken))
				.thenReturn(testUser);

		String state = generateValidState();

		Response response = client.get("/v1/auth/github/landing?code=" + testCode + "&state=" + state);

		assertStatus(HttpStatus.FOUND, response);

		String setCookie = response.header("Set-Cookie");
		assertNotNull(setCookie);
		assertTrue(setCookie.contains("Path=/v1/auth/refresh"), "Cookie path should be /v1/auth/refresh");
	}

	@Test
	void githubLandingRefreshTokenCookieMaxAge() throws Exception {
		String testCode = "test_code";
		String testAccessToken = "gho_token";
		GithubAPI.GithubUser testUser = new GithubAPI.GithubUser(777L, "User", "user");

		Mockito.when(mockGithubApi.accessToken(anyString(), anyString(), eq(testCode), anyString()))
				.thenReturn(testAccessToken);
		Mockito.when(mockGithubApi.getUser(testAccessToken))
				.thenReturn(testUser);

		String state = generateValidState();

		Response response = client.get("/v1/auth/github/landing?code=" + testCode + "&state=" + state);

		assertStatus(HttpStatus.FOUND, response);

		String setCookie = response.header("Set-Cookie");
		assertNotNull(setCookie);
		assertTrue(setCookie.contains("Max-Age=604800"), "Cookie should have 7 day max age (604800 seconds)");
	}

	private String generateValidState() {
		return JWT.create()
			.withIssuer(config.jwt().issuer())
			.withExpiresAt(Instant.now().plus(Duration.ofMinutes(10)))
			.withJWTId(UUID.randomUUID().toString())
			.withClaim("plt", "github")
			.sign(config.jwt().algorithm());
	}

	@Test
	void permissionGroupUserNotInAnyTeam() throws Exception {
		// Setup: User is not in any team (default mock behavior returns false)
		long userId = 12345L;
		String testCode = "test_code_user";
		String testAccessToken = "gho_token_user";
		GithubAPI.GithubUser testUser = new GithubAPI.GithubUser(userId, "Regular User", "regularuser");

		Mockito.when(mockGithubApi.accessToken(anyString(), anyString(), eq(testCode), anyString()))
				.thenReturn(testAccessToken);
		Mockito.when(mockGithubApi.getUser(testAccessToken))
				.thenReturn(testUser);

		String state = generateValidState();

		// Get refresh token via landing
		Response landingResponse = client.get("/v1/auth/github/landing?code=" + testCode + "&state=" + state);
		assertStatus(HttpStatus.FOUND, landingResponse);

		String setCookie = landingResponse.header("Set-Cookie");
		assertNotNull(setCookie);
		String refreshToken = extractCookieValue(setCookie, "refreshToken");

		// Use refresh token to get access token
		Response refreshResponse = client.post("/v1/auth/refresh", null, builder -> {
			builder.addHeader("Cookie", "refreshToken=" + refreshToken);
		});

		assertStatus(HttpStatus.OK, refreshResponse);

		String body = refreshResponse.body().string();
		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		String accessToken = json.get("accessToken").getAsString();

		// Decode and verify the access token has USER role
		DecodedJWT decoded = JWT.decode(accessToken);
		assertEquals("user", decoded.getClaim("role").asString(), "User not in any team should have USER role");
	}

	@Test
	void permissionGroupUserInTrustedTeamOnly() throws Exception {
		// Setup: User is in trusted team but not admin team
		long userId = 23456L;
		String testCode = "test_code_trusted";
		String testAccessToken = "gho_token_trusted";
		GithubAPI.GithubUser testUser = new GithubAPI.GithubUser(userId, "Trusted User", "trusteduser");

		Mockito.when(mockGithubApi.accessToken(anyString(), anyString(), eq(testCode), anyString()))
				.thenReturn(testAccessToken);
		Mockito.when(mockGithubApi.getUser(testAccessToken))
				.thenReturn(testUser);
		Mockito.when(mockGithubApi.isTeamMember(eq("test-org"), eq("admin-team"), eq(userId)))
				.thenReturn(false);
		Mockito.when(mockGithubApi.isTeamMember(eq("test-org"), eq("trusted-team"), eq(userId)))
				.thenReturn(true);

		String state = generateValidState();

		// Get refresh token via landing
		Response landingResponse = client.get("/v1/auth/github/landing?code=" + testCode + "&state=" + state);
		assertStatus(HttpStatus.FOUND, landingResponse);

		String setCookie = landingResponse.header("Set-Cookie");
		assertNotNull(setCookie);
		String refreshToken = extractCookieValue(setCookie, "refreshToken");

		// Use refresh token to get access token
		Response refreshResponse = client.post("/v1/auth/refresh", null, builder -> {
			builder.addHeader("Cookie", "refreshToken=" + refreshToken);
		});

		assertStatus(HttpStatus.OK, refreshResponse);

		String body = refreshResponse.body().string();
		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		String accessToken = json.get("accessToken").getAsString();

		// Decode and verify the access token has TRUSTED role
		DecodedJWT decoded = JWT.decode(accessToken);
		assertEquals("trusted", decoded.getClaim("role").asString(), "User in trusted team should have TRUSTED role");
	}

	@Test
	void permissionGroupUserInAdminTeamOnly() throws Exception {
		// Setup: User is in admin team but not trusted team
		long userId = 34567L;
		String testCode = "test_code_admin";
		String testAccessToken = "gho_token_admin";
		GithubAPI.GithubUser testUser = new GithubAPI.GithubUser(userId, "Admin User", "adminuser");

		Mockito.when(mockGithubApi.accessToken(anyString(), anyString(), eq(testCode), anyString()))
				.thenReturn(testAccessToken);
		Mockito.when(mockGithubApi.getUser(testAccessToken))
				.thenReturn(testUser);
		Mockito.when(mockGithubApi.isTeamMember(eq("test-org"), eq("admin-team"), eq(userId)))
				.thenReturn(true);
		Mockito.when(mockGithubApi.isTeamMember(eq("test-org"), eq("trusted-team"), eq(userId)))
				.thenReturn(false);

		String state = generateValidState();

		// Get refresh token via landing
		Response landingResponse = client.get("/v1/auth/github/landing?code=" + testCode + "&state=" + state);
		assertStatus(HttpStatus.FOUND, landingResponse);

		String setCookie = landingResponse.header("Set-Cookie");
		assertNotNull(setCookie);
		String refreshToken = extractCookieValue(setCookie, "refreshToken");

		// Use refresh token to get access token
		Response refreshResponse = client.post("/v1/auth/refresh", null, builder -> {
			builder.addHeader("Cookie", "refreshToken=" + refreshToken);
		});

		assertStatus(HttpStatus.OK, refreshResponse);

		String body = refreshResponse.body().string();
		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		String accessToken = json.get("accessToken").getAsString();

		// Decode and verify the access token has ADMIN role
		DecodedJWT decoded = JWT.decode(accessToken);
		assertEquals("admin", decoded.getClaim("role").asString(), "User in admin team should have ADMIN role");
	}

	@Test
	void permissionGroupUserInBothTeams() throws Exception {
		// Setup: User is in both admin and trusted teams - admin should take precedence
		long userId = 45678L;
		String testCode = "test_code_both";
		String testAccessToken = "gho_token_both";
		GithubAPI.GithubUser testUser = new GithubAPI.GithubUser(userId, "Super User", "superuser");

		Mockito.when(mockGithubApi.accessToken(anyString(), anyString(), eq(testCode), anyString()))
				.thenReturn(testAccessToken);
		Mockito.when(mockGithubApi.getUser(testAccessToken))
				.thenReturn(testUser);
		Mockito.when(mockGithubApi.isTeamMember(eq("test-org"), eq("admin-team"), eq(userId)))
				.thenReturn(true);
		Mockito.when(mockGithubApi.isTeamMember(eq("test-org"), eq("trusted-team"), eq(userId)))
				.thenReturn(true);

		String state = generateValidState();

		// Get refresh token via landing
		Response landingResponse = client.get("/v1/auth/github/landing?code=" + testCode + "&state=" + state);
		assertStatus(HttpStatus.FOUND, landingResponse);

		String setCookie = landingResponse.header("Set-Cookie");
		assertNotNull(setCookie);
		String refreshToken = extractCookieValue(setCookie, "refreshToken");

		// Use refresh token to get access token
		Response refreshResponse = client.post("/v1/auth/refresh", null, builder -> {
			builder.addHeader("Cookie", "refreshToken=" + refreshToken);
		});

		assertStatus(HttpStatus.OK, refreshResponse);

		String body = refreshResponse.body().string();
		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		String accessToken = json.get("accessToken").getAsString();

		// Decode and verify the access token has ADMIN role (takes precedence)
		DecodedJWT decoded = JWT.decode(accessToken);
		assertEquals("admin", decoded.getClaim("role").asString(), "User in both teams should have ADMIN role (takes precedence)");
	}

	@Test
	void permissionGroupTeamMembershipCheckFailure() throws Exception {
		// Setup: Team membership check throws IOException
		long userId = 56789L;
		String testCode = "test_code_error";
		String testAccessToken = "gho_token_error";
		GithubAPI.GithubUser testUser = new GithubAPI.GithubUser(userId, "Error User", "erroruser");

		Mockito.when(mockGithubApi.accessToken(anyString(), anyString(), eq(testCode), anyString()))
				.thenReturn(testAccessToken);
		Mockito.when(mockGithubApi.getUser(testAccessToken))
				.thenReturn(testUser);
		Mockito.when(mockGithubApi.isTeamMember(anyString(), anyString(), eq(userId)))
				.thenThrow(new IOException("GitHub team API error"));

		String state = generateValidState();

		// Get refresh token via landing
		Response landingResponse = client.get("/v1/auth/github/landing?code=" + testCode + "&state=" + state);
		assertStatus(HttpStatus.FOUND, landingResponse);

		String setCookie = landingResponse.header("Set-Cookie");
		assertNotNull(setCookie);
		String refreshToken = extractCookieValue(setCookie, "refreshToken");

		// Use refresh token to get access token - should fail with 500
		Response refreshResponse = client.post("/v1/auth/refresh", null, builder -> {
			builder.addHeader("Cookie", "refreshToken=" + refreshToken);
		});

		assertStatus(HttpStatus.INTERNAL_SERVER_ERROR, refreshResponse);
	}

	private String extractParameter(String url, String paramName) {
		int startIndex = url.indexOf(paramName + "=");

		if (startIndex == -1) {
			return null;
		}

		startIndex += paramName.length() + 1;

		int endIndex = url.indexOf("&", startIndex);

		if (endIndex == -1) {
			endIndex = url.length();
		}

		return url.substring(startIndex, endIndex);
	}
}
