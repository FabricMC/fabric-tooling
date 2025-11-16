package net.fabricmc.javadoc.api.v1;

import static io.javalin.apibuilder.ApiBuilder.post;

import java.util.Set;

import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.security.RouteRole;

import net.fabricmc.javadoc.auth.AccessTokenController;

public record AuthApi(AccessTokenController accessTokenController) {
	public void handleAccess(Context context) {
		Set<RouteRole> routeRoles = context.routeRoles();

		if (routeRoles.isEmpty()) {
			// Require that all routes specify the required roles
			throw new InternalServerErrorResponse("Missing route roles for " + context.req().getRequestURI());
		}

		if (routeRoles.contains(Role.OPEN)) {
			return; // Anyone can access
		}
	}

	public EndpointGroup endpoints() {
		return () -> {
			post("refresh", this::refresh, Role.OPEN); // Handles its own authentication
		};
	}

	@OpenApi(
		path = "/v1/auth/refresh",
		methods = HttpMethod.POST,
		summary = "Refresh the access token",
		tags = "Auth",
		operationId = "refreshAccessToken",
		responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RefreshResponse.class))
	)
	private void refresh(Context context) {
		String accessToken = accessTokenController.newAccessToken();
		context.json(new RefreshResponse(accessToken));
	}

	private record RefreshResponse(String accessToken) {}
}
