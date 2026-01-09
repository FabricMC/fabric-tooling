package net.fabricmc.annotater.auth.oauth;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTVerificationException;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.UnauthorizedResponse;

import net.fabricmc.annotater.Config;
import net.fabricmc.annotater.auth.AuthPlatform;
import net.fabricmc.annotater.auth.RefreshToken;

public abstract class OAuthProvider {
	private static final Duration STATE_DURATION = Duration.ofMinutes(10);

	private final Config config;
	private final Config.OAuth oAuthConfig;

	private final String platform;

	protected OAuthProvider(Config config, Config.OAuth oAuthConfig) {
		this.config = config;
		this.oAuthConfig = oAuthConfig;

		this.platform = getAuthPlatform().name().toLowerCase(Locale.ROOT);
	}

	protected abstract AuthPlatform getAuthPlatform();

	/**
	 * @return The base URL for authorisation requests.
	 */
	protected abstract String getBaseURL();

	/**
	 * @return The list of scopes required for this OAuth provider.
	 */
	protected abstract List<String> getScopes();

	/**
	 * Verify the user using the provided authorization code, and return the display name.
	 *
	 * @param code The authorization code.
	 * @return The display name of the user.
	 */
	protected abstract RefreshToken.User verifyUser(String code);

	/**
	 * Start the OAuth authorisation process by constructing the authorisation URL.
	 *
	 * @return The complete authorisation URL with parameters.
	 */
	public final String getAuthorisationURL() {
		StringBuilder url = new StringBuilder();
		url.append(getBaseURL());
		url.append("?");
		getParams().forEach((key, value) -> url.append(key).append("=").append(value).append("&"));
		return url.toString();
	}

	/**
	 * Verify the OAuth login response, and return the display name.
	 *
	 * @param code The authorization code.
	 * @param state The state parameter, should match the JWT generated earlier.
	 * @return The display name of the user.
	 */
	public final RefreshToken.User verifyLogin(String code, String state) {
		if (code == null || state == null) {
			throw new BadRequestResponse("Missing code and/or state");
		}

		try {
			// Verify that the request provided a valid state parameter.
			JWT.require(config.jwt().algorithm())
					.withIssuer(config.jwt().issuer())
					.withClaim("plt", platform)
					.build()
					.verify(state);
		} catch (JWTVerificationException e) {
			throw new UnauthorizedResponse("Invalid state parameter");
		}

		return verifyUser(code);
	}

	protected String getRedirectUrl() {
		return config.apiUrl() + "/v1/auth/" + platform + "/landing";
	}

	private Map<String, String> getParams() {
		return Map.of(
			"client_id", oAuthConfig.clientID(),
			"state", generateStateJWT(),
			"redirect_uri", getRedirectUrl(),
			"scope", String.join(" ", getScopes())
		);
	}

	private String generateStateJWT() {
		return JWT.create()
				.withIssuer(config.jwt().issuer())
				.withExpiresAt(Instant.now().plus(STATE_DURATION))
				.withJWTId(UUID.randomUUID().toString())
				.withClaim("plt", platform)
				.sign(config.jwt().algorithm());
	}
}
