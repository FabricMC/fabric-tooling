package net.fabricmc.annotater.auth.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.UnauthorizedResponse;

import net.fabricmc.annotater.Config;
import net.fabricmc.annotater.auth.AccessToken;
import net.fabricmc.annotater.auth.AccessTokenController;
import net.fabricmc.annotater.auth.AuthPlatform;
import net.fabricmc.annotater.auth.PermissionGroup;
import net.fabricmc.annotater.auth.RefreshToken;
import net.fabricmc.annotater.auth.oauth.OAuthProvider;

public record AccessTokenControllerImpl(
		Config config,
		Map<AuthPlatform, OAuthProvider> oAuthProviders
) implements AccessTokenController {
	private static final Duration ACCESS_TOKEN_DURATION = Duration.ofMinutes(5);

	@Override
	public String newAccessToken(RefreshToken refreshToken) {
		OAuthProvider oAuthProvider = oAuthProviders.get(refreshToken.platform());

		if (oAuthProvider == null) {
			throw new UnauthorizedResponse("Unsupported authentication platform: " + refreshToken.platform());
		}

		PermissionGroup permissionGroup = oAuthProvider.getPermissionGroup(refreshToken);

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
