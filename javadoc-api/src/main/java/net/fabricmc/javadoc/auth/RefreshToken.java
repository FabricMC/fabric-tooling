package net.fabricmc.javadoc.auth;

import java.util.Locale;
import java.util.UUID;

import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.interfaces.DecodedJWT;

public record RefreshToken(UUID uuid, AuthPlatform platform, User user) {
	public static RefreshToken fromJwt(DecodedJWT jwt) {
		return new RefreshToken(
				UUID.fromString(jwt.getId()),
				AuthPlatform.getByName(jwt.getClaim("plt").asString()),
				new User(
						jwt.getClaim("userid").asLong(),
						jwt.getSubject()
				)
		);
	}

	public void applyToJWT(JWTCreator.Builder jwt) {
		jwt.withJWTId(UUID.randomUUID().toString())
				.withClaim("plt", platform.name().toLowerCase(Locale.ROOT))
				.withClaim("userid", user.id)
				.withSubject(user.displayName)
				.withClaim("type", "refresh");
	}

	public record User(long id, String displayName) { }
}
