package net.fabricmc.javadoc.auth.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.UnauthorizedResponse;

import net.fabricmc.javadoc.Config;
import net.fabricmc.javadoc.auth.AuthPlatform;
import net.fabricmc.javadoc.auth.RefreshToken;
import net.fabricmc.javadoc.auth.RefreshTokenController;

public record RefreshTokenControllerImpl(Config config) implements RefreshTokenController {
	private static final Duration REFRESH_TOKEN_DURATION = Duration.ofDays(7);

	@Override
	public String newRefreshToken(AuthPlatform platform, String displayName){
		return JWT.create()
				.withIssuer(config().jwt().issuer())
				.withExpiresAt(Instant.now().plus(REFRESH_TOKEN_DURATION))
				.withSubject(displayName)
				.withJWTId(UUID.randomUUID().toString())
				.withClaim("plt", platform.name().toLowerCase(Locale.ROOT))
				.withClaim("type", "refresh")
				.sign(config().jwt().algorithm());
	}

	@Override
	public RefreshToken parseAndValidateRefreshToken(String jwt) {
		try {
			JWTVerifier verifier = JWT.require(config().jwt().algorithm())
					.withIssuer(config().jwt().issuer())
					.withClaim("type", "refresh")
					.withClaimPresence("plt")
					.build();

			DecodedJWT decoded = verifier.verify(jwt);

			RefreshToken token = new RefreshToken(
					UUID.fromString(decoded.getId()),
					AuthPlatform.getByName(decoded.getClaim("plt").asString()),
					decoded.getSubject()
			);

			return token;
		} catch (JWTDecodeException e) {
			throw new BadRequestResponse("Malformed refresh JWT token");
		} catch (JWTVerificationException e) {
			throw new UnauthorizedResponse("Invalid refresh JWT token");
		}
	}
}
