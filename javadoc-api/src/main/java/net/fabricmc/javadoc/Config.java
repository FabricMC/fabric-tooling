package net.fabricmc.javadoc;

import java.nio.file.Path;

public record Config(
		Jwt jwt
) {
	public record Jwt(
			String issuer,
			Path privateKey,
			Path publicKey
	) {};
}
