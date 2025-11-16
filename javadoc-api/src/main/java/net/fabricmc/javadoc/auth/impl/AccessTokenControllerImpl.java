package net.fabricmc.javadoc.auth.impl;

import java.time.Duration;
import java.time.Instant;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import net.fabricmc.javadoc.auth.AccessTokenController;

public class AccessTokenControllerImpl implements AccessTokenController {
	private static final Duration ACCESS_TOKEN_DURATION = Duration.ofMinutes(10);

	private final Algorithm algorithm;

	public AccessTokenControllerImpl(Algorithm algorithm) {
		this.algorithm = algorithm;
	}

	@Override
	public String newAccessToken() {
		return JWT.create()
				.withIssuer("https://fixme.net")
				.withExpiresAt(Instant.now().plus(ACCESS_TOKEN_DURATION))
				.withClaim("type", "access")
				.withClaim("role", "todo")
				.sign(algorithm);
	}
}
