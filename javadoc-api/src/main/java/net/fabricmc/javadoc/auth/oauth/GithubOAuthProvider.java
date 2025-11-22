package net.fabricmc.javadoc.auth.oauth;

import java.io.IOException;
import java.util.List;

import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.UnauthorizedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.javadoc.Config;
import net.fabricmc.javadoc.auth.AuthPlatform;
import net.fabricmc.javadoc.thirdparty.GithubAPI;

// TODO support PKCE
public class GithubOAuthProvider extends OAuthProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger(GithubOAuthProvider.class);

	private final GithubAPI githubAPI;
	private final Config.GithubOAuth githubOAuth;

	public GithubOAuthProvider(Config config, GithubAPI githubAPI) {
		super(config, config.githubOAuth());
		this.githubAPI = githubAPI;
		this.githubOAuth = config.githubOAuth();
	}

	@Override
	protected AuthPlatform getAuthPlatform() {
		return AuthPlatform.GITHUB;
	}

	@Override
	protected String getBaseURL() {
		return "https://github.com/login/oauth/authorize";
	}

	@Override
	protected List<String> getScopes() {
		return List.of(
			"read:user"
		);
	}

	@Override
	protected String verifyUser(String code) {
		String accessToken;

		try {
			accessToken = githubAPI.accessToken(
					githubOAuth.clientID(),
					githubOAuth.clientSecret(),
					code,
					getRedirectUrl()
			);
		} catch (IOException e) {
			LOGGER.error("Failed to get GitHub access token", e);
			throw new UnauthorizedResponse("Failed to verify user with GitHub");
		}

		GithubAPI.GithubUser githubUser;

		try {
			githubUser = githubAPI.getUser(accessToken);
		} catch (IOException e) {
			LOGGER.error("Failed to get GitHub user info", e);
			throw new InternalServerErrorResponse();
		}

		LOGGER.info("Verified GitHub user: {} (ID: {})", githubUser.login(), githubUser.id());
		return githubUser.name();
	}
}
