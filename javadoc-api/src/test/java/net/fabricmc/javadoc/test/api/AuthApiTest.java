package net.fabricmc.javadoc.test.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.HttpStatus;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

import net.fabricmc.javadoc.auth.AuthPlatform;
import net.fabricmc.javadoc.auth.RefreshToken;
import net.fabricmc.javadoc.auth.impl.RefreshTokenControllerImpl;
import net.fabricmc.javadoc.test.AbstractApiTest;

public class AuthApiTest extends AbstractApiTest {
	@Test
	void refreshAccessTokenSuccess() throws Exception {
		String jwt = new RefreshTokenControllerImpl(config).newRefreshToken(AuthPlatform.DISCORD, new RefreshToken.User(1, "Test User"));

		Response response = client.post("/v1/auth/refresh", null, builder -> {
			builder.addHeader("Cookie", "refreshToken=" + jwt);
		});

		assertStatus(HttpStatus.OK, response);

		String body = response.body().string();
		assertNotNull(body);
		assertFalse(body.isEmpty());

		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		String accessToken = json.get("accessToken").getAsString();
		assertNotNull(accessToken);
		assertFalse(accessToken.isEmpty());

		// Use the access token to call the check endpoint
		Response checkResponse = client.get("/v1/auth/check", builder -> {
			builder.addHeader("Authorization", "Bearer " + accessToken);
		});

		assertStatus(HttpStatus.OK, checkResponse);
	}

	@Test
	void refreshAccessTokenInvalidRefreshToken() {
		Response response = client.post("/v1/auth/refresh", null, builder -> {
			builder.addHeader("Cookie", "refreshToken=invalid");
		});

		assertStatus(HttpStatus.BAD_REQUEST, response);
	}

	@Test
	void refreshAccessTokenExpired() {
		String jwt = JWT.create()
				.withIssuer(config.jwt().issuer())
				.withExpiresAt(Instant.now().minus(Duration.ofDays(1)))
				.withSubject("TestUser")
				.withJWTId(UUID.randomUUID().toString())
				.withClaim("plt", "discord")
				.withClaim("type", "refresh")
				.sign(config.jwt().algorithm());

		Response response = client.post("/v1/auth/refresh", null, builder -> {
			builder.addHeader("Cookie", "refreshToken=" + jwt);
		});

		assertStatus(HttpStatus.UNAUTHORIZED, response);
	}

	@Test
	void checkValidAccessToken() {
		Response checkResponse = client.get("/v1/auth/check", builder -> {
			withAccessToken(builder);
		});

		assertStatus(HttpStatus.OK, checkResponse);
	}

	@Test
	void checkMissingAccessToken() {
		Response response = client.get("/v1/auth/check");

		assertStatus(HttpStatus.UNAUTHORIZED, response);
	}

	@Test
	void checkInvalidAccessToken() {
		Response response = client.get("/v1/auth/check", builder -> {
			builder.addHeader("Authorization", "Bearer invalid_token");
		});

		assertStatus(HttpStatus.BAD_REQUEST, response);
	}

	@Test
	void checkExpiredAccessToken() {
		String jwt = JWT.create()
				.withIssuer(config.jwt().issuer())
				.withExpiresAt(Instant.now().minus(Duration.ofMinutes(1)))
				.withSubject("TestUser")
				.withJWTId(UUID.randomUUID().toString())
				.withClaim("plt", "github")
				.withClaim("type", "access")
				.withClaim("role", "user")
				.withClaim("userid", 0)
				.sign(config.jwt().algorithm());

		Response response = client.get("/v1/auth/check", builder -> {
			builder.addHeader("Authorization", "Bearer " + jwt);
		});

		assertStatus(HttpStatus.UNAUTHORIZED, response);
	}

	@Test
	void checkMalformedAuthorizationHeader() {
		Response response = client.get("/v1/auth/check", builder -> {
			builder.addHeader("Authorization", "InvalidFormat");
		});

		assertStatus(HttpStatus.UNAUTHORIZED, response);
	}

	@Test
	void checkWrongTokenType() {
		// Create a refresh token instead of an access token
		String jwt = JWT.create()
				.withIssuer(config.jwt().issuer())
				.withExpiresAt(Instant.now().plus(Duration.ofDays(1)))
				.withSubject("TestUser")
				.withJWTId(UUID.randomUUID().toString())
				.withClaim("plt", "github")
				.withClaim("type", "refresh")
				.sign(config.jwt().algorithm());

		Response response = client.get("/v1/auth/check", builder -> {
			builder.addHeader("Authorization", "Bearer " + jwt);
		});

		assertStatus(HttpStatus.UNAUTHORIZED, response);
	}
}
