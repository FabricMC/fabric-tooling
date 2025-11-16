package net.fabricmc.javadoc.auth;

public enum PermissionGroup {
	/**
	 * Regular user with standard permissions.
	 */
	USER,
	/**
	 * Trusted user with elevated permissions.
	 */
	TRUSTED,
	/**
	 * Administrator with full permissions.
	 */
	ADMIN
}
