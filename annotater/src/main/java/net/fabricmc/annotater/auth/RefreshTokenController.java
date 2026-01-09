package net.fabricmc.annotater.auth;

public interface RefreshTokenController {
	/**
	 * Generates a new refresh token for the given platform and display name.
	 *
	 * @param platform The authentication platform.
	 * @param user The user details
	 * @return A JWT of the refresh token.
	 */
	String newRefreshToken(AuthPlatform platform, RefreshToken.User user);

	/**
	 * Parses and validates the given refresh token JWT.
	 *
	 * @param jwt The JWT of the refresh token.
	 * @return The parsed and validated RefreshToken.
	 */
	RefreshToken parseAndValidateRefreshToken(String jwt);
}
