package net.fabricmc.javadoc;

import java.nio.file.Path;

public record Config(
		// The base url of this API. e.g "https://api.example.com"
		String apiUrl,
		Jwt jwt,
		GithubOAuth githubOAuth
) {
	public record Jwt(
			// The issuer field for the JWTs issued by this API, e.g "api.example.com"
			String issuer,
			String privateKey,
			String publicKey
	) {}

	public interface OAuth {
		String clientID();
		String clientSecret();
	}

	public record GithubOAuth(
			String clientID,
			String clientSecret
	) implements OAuth {}
}
