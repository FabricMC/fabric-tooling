package net.fabricmc.javadoc.auth;

import java.util.Locale;
import java.util.UUID;

import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.interfaces.DecodedJWT;

public record AccessToken(UUID uuid, PermissionGroup role, AuthPlatform platform, User user) {
	public static AccessToken fromJwt(DecodedJWT jwt) {
		return new AccessToken(
				UUID.fromString(jwt.getId()),
				PermissionGroup.getByName(jwt.getClaim("role").asString()),
				AuthPlatform.getByName(jwt.getClaim("plt").asString()),
				new User(
						jwt.getClaim("userid").asLong(),
						jwt.getSubject()
				)
		);
	}

	public void applyToJWT(JWTCreator.Builder jwt) {
		jwt.withJWTId(uuid.toString())
				.withClaim("role", role.name().toLowerCase(Locale.ROOT))
				.withClaim("plt", platform.name().toLowerCase(Locale.ROOT))
				.withClaim("userid", user.id)
				.withSubject(user.displayName)
				.withClaim("type", "access");
	}

	public record User(long id, String displayName) { }
}
