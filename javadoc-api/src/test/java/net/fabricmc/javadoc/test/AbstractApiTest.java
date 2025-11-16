package net.fabricmc.javadoc.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import com.auth0.jwt.algorithms.Algorithm;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.testtools.DefaultTestConfig;
import io.javalin.testtools.HttpClient;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import net.fabricmc.javadoc.Config;
import net.fabricmc.javadoc.api.ApiServer;
import net.fabricmc.javadoc.api.v1.AuthApi;
import net.fabricmc.javadoc.auth.impl.AccessTokenControllerImpl;
import net.fabricmc.javadoc.auth.impl.RefreshTokenControllerImpl;

public abstract class AbstractApiTest {
	@TempDir
	private static Path tempDir;

	protected static Config config;
	protected static Algorithm jwtAlgorithm;
	protected static RefreshTokenControllerImpl refreshTokenController;
	protected static AccessTokenControllerImpl accessTokenController;

	protected ApiServer server;
	protected Javalin app;
	protected HttpClient client;

	@BeforeAll
	static void setUpClass() throws Exception {
		generateJWTKeyPair();

		config = new Config(new Config.Jwt("https://localhost", tempDir.resolve("private_key.pem"), tempDir.resolve("public_key.pem")));
		jwtAlgorithm = Algorithm.ECDSA384(new net.fabricmc.javadoc.util.KeyPair(config.jwt().publicKey(), config.jwt().privateKey()));

		refreshTokenController = new RefreshTokenControllerImpl(jwtAlgorithm, config);
		accessTokenController = new AccessTokenControllerImpl(jwtAlgorithm, config);
	}

	@BeforeEach
	void setUp() {
		AuthApi authApi = new AuthApi(accessTokenController, refreshTokenController);
		server = new ApiServer(authApi);

		app = server.getApp();
		app.start(0);

		client = new HttpClient(app, DefaultTestConfig.getOkHttpClient());
	}

	@AfterEach
	void tearDown() {
		app.stop();
	}

	protected void assertStatus(HttpStatus status, Response response) {
		Assertions.assertEquals(status.getCode(), response.code(), "Expected HTTP status " + status + " but got " + HttpStatus.forStatus(response.code()));
	}

	private static void generateJWTKeyPair() throws Exception {
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
		kpg.initialize(new ECGenParameterSpec("secp384r1"));
		KeyPair keyPair = kpg.generateKeyPair();

		byte[] privateDer = keyPair.getPrivate().getEncoded();
		writePemFile(tempDir.resolve("private_key.pem"), "PRIVATE KEY", privateDer);

		byte[] publicDer = keyPair.getPublic().getEncoded();
		writePemFile(tempDir.resolve("public_key.pem"), "PUBLIC KEY", publicDer);
	}

	private static void writePemFile(Path file, String type, byte[] derBytes) throws IOException {
		String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(derBytes);

		String pem = "-----BEGIN " + type + "-----\n"
				+ base64
				+ "\n-----END " + type + "-----\n";

		Files.write(file, pem.getBytes());
	}
}
