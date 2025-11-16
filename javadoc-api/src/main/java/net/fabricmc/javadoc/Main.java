package net.fabricmc.javadoc;

import java.io.IOException;
import java.nio.file.Path;

import com.auth0.jwt.algorithms.Algorithm;

import net.fabricmc.javadoc.api.ApiServer;
import net.fabricmc.javadoc.api.v1.AuthApi;
import net.fabricmc.javadoc.auth.impl.AccessTokenControllerImpl;
import net.fabricmc.javadoc.util.KeyPair;

public class Main {
	public static void main(String[] args) throws IOException {
		Algorithm jwtAlgorithm = Algorithm.ECDSA384(new KeyPair(Path.of("public_key.pem"), Path.of("private_key.pem")));

		var accessTokenController = new AccessTokenControllerImpl(jwtAlgorithm);
		var authApi = new AuthApi(accessTokenController);
		var apiServer = new ApiServer(authApi);

		apiServer.run();
	}
}
