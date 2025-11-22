package net.fabricmc.javadoc.thirdparty;

import java.io.IOException;

public interface GithubAPI {
	/**
	 * Exchange the oauth code for an access token.
	 */
	String accessToken(String clientId, String clientSecret, String code, String redirectUrl) throws IOException;

	/**
	 * Get the GitHub user associated with the given access token.
	 */
	GithubUser getUser(String token) throws IOException;

	record GithubUser(
			long id,
			String name,
			String login
	) { }
}
