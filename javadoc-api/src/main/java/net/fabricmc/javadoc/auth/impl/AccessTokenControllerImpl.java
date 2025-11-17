package net.fabricmc.javadoc.auth.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import com.auth0.jwt.JWT;

import net.fabricmc.javadoc.Config;
import net.fabricmc.javadoc.auth.AccessTokenController;
import net.fabricmc.javadoc.auth.PermissionGroup;
import net.fabricmc.javadoc.auth.RefreshToken;

public record AccessTokenControllerImpl(Config config) implements AccessTokenController {
	private static final Duration ACCESS_TOKEN_DURATION = Duration.ofMinutes(10);

	@Override
	public String newAccessToken(RefreshToken refreshToken) {
		// TODO figure out a way to trust/admin users
		PermissionGroup permissionGroup = PermissionGroup.USER;

		return JWT.create()
				.withIssuer(config.jwt().issuer())
				.withExpiresAt(Instant.now().plus(ACCESS_TOKEN_DURATION))
				.withSubject(refreshToken.displayName())
				.withClaim("type", "access")
				.withClaim("role", permissionGroup.name().toLowerCase(Locale.ROOT))
				.sign(config().jwt().algorithm());
	}
}
