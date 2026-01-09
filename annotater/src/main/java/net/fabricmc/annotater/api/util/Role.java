package net.fabricmc.annotater.api.util;

import io.javalin.security.RouteRole;

/**
 * The authentication roles for a given API endpoint.
 *
 * <p>If unsure choose {@link Role#ACCESS_TOKEN}
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
	 * API endpoint requires an access token. (The default choice)
	 */
	ACCESS_TOKEN
}
