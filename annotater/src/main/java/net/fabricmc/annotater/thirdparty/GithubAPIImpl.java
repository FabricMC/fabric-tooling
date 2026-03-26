package net.fabricmc.annotater.thirdparty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class GithubAPIImpl implements GithubAPI {
	private static final Gson GSON = new Gson();
	private static final String API_VERSION = "2022-11-28";
	private final String apiToken;

	public GithubAPIImpl(String apiToken) {
		this.apiToken = apiToken;
	}

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
					.header("X-GitHub-Api-Version", API_VERSION)
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

	// https://docs.github.com/en/rest/teams/members#list-team-members
	@Override
	public boolean isTeamMember(String org, String teamSlug, long userId) throws IOException {
		try (HttpClient httpClient = HttpClient.newHttpClient()) {
			// List all team members and check if the user ID is in the list
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create("https://api.github.com/orgs/" + org + "/teams/" + teamSlug + "/members"))
					.header("Accept", "application/vnd.github+json")
					.header("Authorization", "Bearer " + apiToken)
					.header("X-GitHub-Api-Version", API_VERSION)
					.build();

			HttpResponse<String> response;

			try {
				response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}

			// 200 = success, 404 = team doesn't exist or no access
			if (response.statusCode() == 404) {
				return false;
			} else if (response.statusCode() != 200) {
				throw new IOException("Failed to check team membership: " + response.statusCode() + " " + response.body());
			}

			// Parse the response to check if user ID is in the list
			TeamMember[] members = GSON.fromJson(response.body(), TeamMember[].class);

			for (TeamMember member : members) {
				if (member.id() == userId) {
					return true;
				}
			}

			return false;
		}
	}

	private record TeamMember(
			long id,
			String login
	) { }

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
