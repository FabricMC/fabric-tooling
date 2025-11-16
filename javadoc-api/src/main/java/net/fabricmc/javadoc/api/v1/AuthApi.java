package net.fabricmc.javadoc.api.v1;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;

import java.util.Set;

import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.security.RouteRole;

import net.fabricmc.javadoc.auth.AccessTokenController;
import net.fabricmc.javadoc.auth.RefreshToken;
import net.fabricmc.javadoc.auth.RefreshTokenController;

public record AuthApi(AccessTokenController accessTokenController, RefreshTokenController refreshTokenController) {
	public void handleAccess(Context context) {
		Set<RouteRole> routeRoles = context.routeRoles();

		if (routeRoles.isEmpty()) {
			// Require that all routes specify the required roles
			throw new InternalServerErrorResponse("Missing route roles for " + context.req().getRequestURI());
		} else if (routeRoles.size() != 1) {
			// Each route should only have one role
			throw new InternalServerErrorResponse("Multiple route roles for " + context.req().getRequestURI() + ": " + routeRoles);
		}

		Role role = (Role) routeRoles.iterator().next();

		switch (role) {
			case OPEN -> {
				return; // Anyone can access
			}
			case REFRESH_TOKEN -> {
				String jwt = context.cookie("refreshToken");

				if (jwt == null) {
					throw new UnauthorizedResponse("Missing refresh token cookie");
				}

				RefreshToken refreshToken = refreshTokenController.parseAndValidateRefreshToken(jwt);
				Attributes.REFRESH_TOKEN.set(context, refreshToken);
				return;
			}
			case AUTH_TOKEN -> {
				break;
			}
		}

		throw new UnauthorizedResponse();
	}

	public EndpointGroup endpoints() {
		return () -> {
			get("discord", this::discord, Role.OPEN);
			get("github", this::github, Role.OPEN);
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
		context.json(new DiscordResponse("https://example.com"));
	}

	private record DiscordResponse(String url) {}

	@OpenApi(
			path = "/v1/auth/github",
			methods = HttpMethod.GET,
			summary = "Request a Github OAuth url",
			tags = "Auth",
			operationId = "githubAuth",
			responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = GitHubResponse.class))
	)
	private void github(Context context) {
		context.json(new DiscordResponse("https://github.com"));
	}

	private record GitHubResponse(String url) {}

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
		RefreshToken refreshToken = Attributes.REFRESH_TOKEN.get(context);
		String accessToken = accessTokenController.newAccessToken(refreshToken);
		context.json(new RefreshResponse(accessToken));
	}

	private record RefreshResponse(String accessToken) {}
}
