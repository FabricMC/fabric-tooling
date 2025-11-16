package net.fabricmc.javadoc.auth;

public enum AuthPlatform {
	GITHUB,
	DISCORD,
	BOT;

	public static AuthPlatform getByName(String name) {
		for (AuthPlatform platform : values()) {
			if (platform.name().equalsIgnoreCase(name)) {
				return platform;
			}
		}

		throw new IllegalArgumentException("No AuthPlatform with name: " + name);
	}
}
