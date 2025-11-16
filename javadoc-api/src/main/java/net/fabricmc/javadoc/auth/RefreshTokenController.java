package net.fabricmc.javadoc.auth;

public interface RefreshTokenController {
	/**
	 * Generates a new refresh token for the given platform and display name.
	 *
	 * @param platform The authentication platform.
	 * @param displayName The display name of the user.
	 * @return A JWT of the refresh token.
	 */
	String newRefreshToken(AuthPlatform platform, String displayName);

	/**
	 * Parses and validates the given refresh token JWT.
	 *
	 * @param jwt The JWT of the refresh token.
	 * @return The parsed and validated RefreshToken.
	 */
	RefreshToken parseAndValidateRefreshToken(String jwt);
}
