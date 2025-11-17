package net.fabricmc.javadoc.api.util;

import java.util.Objects;

import io.javalin.http.Context;

import net.fabricmc.javadoc.auth.RefreshToken;

public record Attributes<T>(String key) {
	public static Attributes<RefreshToken> REFRESH_TOKEN = new Attributes<>("refreshToken");

	public T get(Context context) {
		return Objects.requireNonNull(context.attribute(this.key));
	}

	public void set(Context context, T value) {
		Objects.requireNonNull(value, "value cannot be null");
		context.attribute(this.key, value);
	}
}
