package net.fabricmc.javadoc;

import java.io.IOException;

import com.auth0.jwt.algorithms.Algorithm;

import net.fabricmc.javadoc.api.ApiServer;
import net.fabricmc.javadoc.api.v1.AuthApi;
import net.fabricmc.javadoc.auth.impl.AccessTokenControllerImpl;
import net.fabricmc.javadoc.auth.impl.RefreshTokenControllerImpl;
import net.fabricmc.javadoc.util.KeyPair;

public class Main {
	public static void main(String[] args) throws IOException {
		Config config = new Config(null); // TODO load the config from json?

		Algorithm jwtAlgorithm = Algorithm.ECDSA384(new KeyPair(config.jwt().publicKey(), config.jwt().privateKey()));

		var refreshTokenController = new RefreshTokenControllerImpl(jwtAlgorithm, config);
		var accessTokenController = new AccessTokenControllerImpl(jwtAlgorithm, config);
		var authApi = new AuthApi(accessTokenController, refreshTokenController);
		var apiServer = new ApiServer(authApi);

		apiServer.run();
	}
}
