package net.fabricmc.javadoc.api.v1;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;
import io.javalin.http.util.NaiveRateLimit;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.javadoc.Config;
import net.fabricmc.javadoc.api.util.Attributes;
import net.fabricmc.javadoc.api.util.Role;
import net.fabricmc.javadoc.auth.AccessTokenController;
import net.fabricmc.javadoc.auth.AuthPlatform;
import net.fabricmc.javadoc.auth.RefreshToken;
import net.fabricmc.javadoc.auth.RefreshTokenController;
import net.fabricmc.javadoc.auth.oauth.GithubOAuthProvider;

public record AuthApi(
		Config config,
		AccessTokenController accessTokenController,
		RefreshTokenController refreshTokenController,
		GithubOAuthProvider githubOAuthProvider) {
	private static final Logger LOGGER = LoggerFactory.getLogger(AuthApi.class);

	public EndpointGroup endpoints() {
		return () -> {
			get("discord", this::discord, Role.OPEN);
			get("github", this::github, Role.OPEN);
			get("github/landing", this::githubLanding, Role.OPEN);
			post("refresh", this::refresh, Role.REFRESH_TOKEN);
		};
	}

	@OpenApi(
			path = "/v1/auth/discord",
			methods = HttpMethod.GET,
			summary = "Request a Discord OAuth url",
			tags = "Auth",
			operationId = "discordAuth",
			responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DiscordResponse.class))
	)
	private void discord(Context context) {
		NaiveRateLimit.requestPerTimeUnit(context, 5, TimeUnit.MINUTES);

		context.json(new DiscordResponse("https://example.com"));
	}

	private record DiscordResponse(String url) { }

	@OpenApi(
			path = "/v1/auth/github",
			methods = HttpMethod.GET,
			summary = "Request a Github OAuth url",
			tags = "Auth",
			operationId = "githubAuth",
			responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = GitHubResponse.class))
	)
	private void github(Context context) {
		NaiveRateLimit.requestPerTimeUnit(context, 5, TimeUnit.MINUTES);
		LOGGER.info("Providing Github OAuth URL to {}", context.req().getRemoteAddr());

		String authorisationURL = githubOAuthProvider.getAuthorisationURL();
		context.json(new GitHubResponse(authorisationURL));
	}

	private record GitHubResponse(String url) { }

	@OpenApi(
			path = "/v1/auth/github/landing",
			methods = HttpMethod.GET,
			summary = "Github OAuth landing endpoint",
			tags = "Auth",
			queryParams = {
				@OpenApiParam(name = "code", required = true, description = "The OAuth code from Github"),
				@OpenApiParam(name = "state", required = true, description = "The OAuth state from Github")
			},
			responses = @OpenApiResponse(status = "302")
	)
	private void githubLanding(Context context) {
		NaiveRateLimit.requestPerTimeUnit(context, 5, TimeUnit.MINUTES);

		String code = context.queryParam("code");
		String state = context.queryParam("state");

		String displayName = githubOAuthProvider().verifyLogin(code, state);
		redirectWithRefreshToken(context, AuthPlatform.GITHUB, displayName);
	}

	private void redirectWithRefreshToken(Context context, AuthPlatform authPlatform, String displayName) {
		LOGGER.info("Creating refresh token for {} user {}", authPlatform, displayName);

		String refreshToken = refreshTokenController().newRefreshToken(authPlatform, displayName);
		Cookie refreshTokenCookie = new Cookie(
				"refreshToken",
				refreshToken,
				"/v1/auth/refresh",
				(int) Duration.ofDays(7).toSeconds(),
				true, // Secure
				0,
				true, // HTTP only
				null,
				null,
				SameSite.STRICT
		);
		context.cookie(refreshTokenCookie);
		context.redirect(config().apiUrl());
	}

	@OpenApi(
			path = "/v1/auth/refresh",
			methods = HttpMethod.POST,
			summary = "Refresh the access token",
			cookies = {@OpenApiParam(name = "refreshToken", description = "The refresh token cookie", required = true)},
			tags = "Auth",
			operationId = "refreshAccessToken",
			responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RefreshResponse.class))
	)
	private void refresh(Context context) {
		NaiveRateLimit.requestPerTimeUnit(context, 5, TimeUnit.MINUTES);

		RefreshToken refreshToken = Attributes.REFRESH_TOKEN.get(context);
		String accessToken = accessTokenController.newAccessToken(refreshToken);
		context.json(new RefreshResponse(accessToken));
	}

	private record RefreshResponse(String accessToken) { }
}
