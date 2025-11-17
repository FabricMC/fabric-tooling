package net.fabricmc.javadoc.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.Gson;
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
	private static final Gson GSON = new Gson();

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
		config = GSON.fromJson(readResource("config.json"), Config.class);
		jwtAlgorithm = Algorithm.ECDSA384(generateJWTKeyPair());

		refreshTokenController = new RefreshTokenControllerImpl(jwtAlgorithm, config);
		accessTokenController = new AccessTokenControllerImpl(jwtAlgorithm, config);
	}

	@BeforeEach
	void setUp() {
		AuthApi authApi = new AuthApi(config, accessTokenController, refreshTokenController, null);
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

	private static net.fabricmc.javadoc.util.KeyPair generateJWTKeyPair() throws Exception {
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
		kpg.initialize(new ECGenParameterSpec("secp384r1"));
		KeyPair keyPair = kpg.generateKeyPair();

		byte[] privateDer = keyPair.getPrivate().getEncoded();
		Path privateKey = writePemFile(tempDir.resolve("private_key.pem"), "PRIVATE KEY", privateDer);

		byte[] publicDer = keyPair.getPublic().getEncoded();
		Path publicKey = writePemFile(tempDir.resolve("public_key.pem"), "PUBLIC KEY", publicDer);

		return new net.fabricmc.javadoc.util.KeyPair(publicKey, privateKey);
	}

	private static Path writePemFile(Path file, String type, byte[] derBytes) throws IOException {
		String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(derBytes);

		String pem = "-----BEGIN " + type + "-----\n"
				+ base64
				+ "\n-----END " + type + "-----\n";

		return Files.write(file, pem.getBytes());
	}

	private static String readResource(String name) {
		try (InputStream is = AbstractApiTest.class.getClassLoader().getResourceAsStream(name)) {
			return new String(is.readAllBytes());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
