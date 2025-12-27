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
	ADMIN;

	public static PermissionGroup getByName(String name) {
		for (PermissionGroup group : values()) {
			if (group.name().equalsIgnoreCase(name)) {
				return group;
			}
		}

		throw new IllegalArgumentException("No PermissionGroup with name: " + name);
	}
}
