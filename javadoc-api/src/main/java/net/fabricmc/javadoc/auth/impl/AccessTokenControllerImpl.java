package net.fabricmc.javadoc.auth.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.UnauthorizedResponse;

import net.fabricmc.javadoc.Config;
import net.fabricmc.javadoc.auth.AccessToken;
import net.fabricmc.javadoc.auth.AccessTokenController;
import net.fabricmc.javadoc.auth.PermissionGroup;
import net.fabricmc.javadoc.auth.RefreshToken;

public record AccessTokenControllerImpl(Config config) implements AccessTokenController {
	private static final Duration ACCESS_TOKEN_DURATION = Duration.ofMinutes(5);

	@Override
	public String newAccessToken(RefreshToken refreshToken) {
		// TODO figure out a way to trust/admin users
		PermissionGroup permissionGroup = PermissionGroup.USER;

		AccessToken accessToken = new AccessToken(
				UUID.randomUUID(),
				permissionGroup,
				refreshToken.platform(),
				new AccessToken.User(
					refreshToken.user().id(),
					refreshToken.user().displayName()
				)
		);

		JWTCreator.Builder builder = JWT.create()
				.withIssuer(config().jwt().issuer())
				.withExpiresAt(Instant.now().plus(ACCESS_TOKEN_DURATION));
		accessToken.applyToJWT(builder);

		return builder
				.sign(config().jwt().algorithm());
	}

	@Override
	public AccessToken parseAndValidateAccessToken(String jwt) {
		try {
			JWTVerifier verifier = JWT.require(config().jwt().algorithm())
					.withIssuer(config().jwt().issuer())
					.withClaim("type", "access")
					.withClaimPresence("plt")
					.build();

			DecodedJWT decoded = verifier.verify(jwt);
			return AccessToken.fromJwt(decoded);
		} catch (JWTDecodeException e) {
			throw new BadRequestResponse("Malformed access JWT token");
		} catch (JWTVerificationException e) {
			throw new UnauthorizedResponse("Invalid access JWT token");
		}
	}
}
