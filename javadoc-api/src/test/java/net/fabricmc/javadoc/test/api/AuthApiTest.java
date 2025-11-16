package net.fabricmc.javadoc.test.api;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.auth0.jwt.JWT;
import io.javalin.http.HttpStatus;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

import net.fabricmc.javadoc.auth.AuthPlatform;
import net.fabricmc.javadoc.test.AbstractApiTest;

public class AuthApiTest extends AbstractApiTest {
	@Test
	void refreshAccessTokenSuccess() {
		String jwt = refreshTokenController.newRefreshToken(AuthPlatform.DISCORD, "Test User");

		Response response = client.post("/v1/auth/refresh", null, builder -> {
			builder.addHeader("Cookie", "refreshToken=" + jwt);
		});

		assertStatus(HttpStatus.OK, response);
		// TODO validate response body
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
				.sign(jwtAlgorithm);

		Response response = client.post("/v1/auth/refresh", null, builder -> {
			builder.addHeader("Cookie", "refreshToken=" + jwt);
		});

		assertStatus(HttpStatus.UNAUTHORIZED, response);
	}
}
