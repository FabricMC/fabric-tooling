package net.fabricmc.javadoc.auth;

public interface AccessTokenController {
	/**
	 * Generates a new access token JWT based on the provided refresh token.
	 *
	 * @param refreshToken The refresh token used to generate the access token.
	 * @param permissionGroup The permission group associated with the access token.
	 * @return A new access token JWT as a String.
	 */
	String newAccessToken(RefreshToken refreshToken);
}
