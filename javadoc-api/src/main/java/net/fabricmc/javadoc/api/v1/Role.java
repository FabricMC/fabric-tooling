package net.fabricmc.javadoc.api.v1;

import io.javalin.security.RouteRole;

public enum Role implements RouteRole {
	OPEN,
	LOGGED_IN,
	TRUSTED,
	ADMIN
}
