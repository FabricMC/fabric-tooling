package net.fabricmc.javadoc.thirdparty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class GithubAPIImpl implements GithubAPI {
	private static final Gson GSON = new Gson();

	// https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#2-users-are-redirected-back-to-your-site-by-github
	@Override
	public String accessToken(String clientId, String clientSecret, String code, String redirectUrl) throws IOException {
		String body = GSON.toJson(new AccessTokenBody(clientId, clientSecret, code, redirectUrl));

		try (HttpClient httpClient = HttpClient.newHttpClient()) {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create("https://github.com/login/oauth/access_token"))
					.header("Accept", "application/json")
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body))
					.build();

			HttpResponse<String> response;

			try {
				response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}

			if (response.statusCode() != 200) {
				throw new IOException("Failed to get access token from GitHub: " + response.body());
			}

			AccessTokenResponse accessTokenResponse = GSON.fromJson(response.body(), AccessTokenResponse.class);
			return accessTokenResponse.accessToken();
		}
	}

	@Override
	public GithubUser getUser(String token) throws IOException {
		try (HttpClient httpClient = HttpClient.newHttpClient()) {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create("https://api.github.com/user"))
					.header("Accept", "application/json")
					.header("Authorization", "Bearer " + token)
					.build();

			HttpResponse<String> response;

			try {
				response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}

			if (response.statusCode() != 200) {
				throw new IOException("Failed to get user from GitHub: " + response.body());
			}

			return GSON.fromJson(response.body(), GithubUser.class);
		}
	}

	private record AccessTokenBody(
			@SerializedName("client_id") String clientId,
			@SerializedName("client_secret") String clientSecret,
			@SerializedName("code") String code,
			@SerializedName("redirect_uri") String redirectUri
	) { }

	private record AccessTokenResponse(
			@SerializedName("access_token") String accessToken,
			@SerializedName("token_type") String tokenType,
			@SerializedName("scope") String scope
	) { }
}
