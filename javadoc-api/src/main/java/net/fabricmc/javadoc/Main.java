package net.fabricmc.javadoc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.auth0.jwt.algorithms.Algorithm;

import com.google.gson.Gson;

import net.fabricmc.javadoc.api.ApiServer;
import net.fabricmc.javadoc.api.v1.AuthApi;
import net.fabricmc.javadoc.auth.impl.AccessTokenControllerImpl;
import net.fabricmc.javadoc.auth.impl.RefreshTokenControllerImpl;
import net.fabricmc.javadoc.auth.oauth.GithubOAuthProvider;
import net.fabricmc.javadoc.thirdparty.GithubAPIImpl;
import net.fabricmc.javadoc.util.KeyPair;

public class Main {
	private static final Gson GSON = new Gson();

	public static void main(String[] args) throws IOException {
		Config config = GSON.fromJson(Files.readString(Path.of("config.json")), Config.class);

		Algorithm jwtAlgorithm = Algorithm.ECDSA384(new KeyPair(Path.of(config.jwt().publicKey()), Path.of(config.jwt().privateKey())));

		var refreshTokenController = new RefreshTokenControllerImpl(jwtAlgorithm, config);
		var accessTokenController = new AccessTokenControllerImpl(jwtAlgorithm, config);
		var githubAPI = new GithubAPIImpl();
		var githubOAuthProvider = new GithubOAuthProvider(config, jwtAlgorithm, githubAPI);
		var authApi = new AuthApi(config, accessTokenController, refreshTokenController, githubOAuthProvider);
		var apiServer = new ApiServer(authApi);

		apiServer.run();
	}
}
