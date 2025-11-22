package net.fabricmc.javadoc.api.v1;

import java.util.Set;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.security.RouteRole;
import org.jetbrains.annotations.NotNull;

import net.fabricmc.javadoc.api.util.Attributes;
import net.fabricmc.javadoc.api.util.Role;
import net.fabricmc.javadoc.auth.RefreshToken;
import net.fabricmc.javadoc.auth.RefreshTokenController;

public record RoleAuthenticationHandler(RefreshTokenController refreshTokenController) implements Handler {
	@Override
	public void handle(@NotNull Context ctx) throws Exception {
		Set<RouteRole> routeRoles = ctx.routeRoles();

		if (routeRoles.isEmpty()) {
			// Require that all routes specify the required roles
			throw new InternalServerErrorResponse("Missing route roles for " + ctx.req().getRequestURI());
		} else if (routeRoles.size() != 1) {
			// Each route should only have one role
			throw new InternalServerErrorResponse("Multiple route roles for " + ctx.req().getRequestURI() + ": " + routeRoles);
		}

		Role role = (Role) routeRoles.iterator().next();

		switch (role) {
		case OPEN -> {
			return; // Anyone can access
		}
		case REFRESH_TOKEN -> {
			String jwt = ctx.cookie("refreshToken");

			if (jwt == null) {
				throw new UnauthorizedResponse("Missing refresh token cookie");
			}

			RefreshToken refreshToken = refreshTokenController.parseAndValidateRefreshToken(jwt);
			Attributes.REFRESH_TOKEN.set(ctx, refreshToken);
			return;
		}
		case AUTH_TOKEN -> {
			break;
		}
		}

		throw new UnauthorizedResponse();
	}
}
