package net.fabricmc.javadoc.api.v1;

import io.javalin.security.RouteRole;

/**
 * The authentication roles for a given API endpoint.
 *
 * If unsure choose {@link Role#AUTH_TOKEN}
 */
public enum Role implements RouteRole {
	/**
	 * API endpoint is open to everyone.
	 */
	OPEN,

	/**
	 * API endpoint requires a refresh token.
	 */
	REFRESH_TOKEN,

	/**
	 * API endpoint requires an access token.
	 */
	AUTH_TOKEN
}
