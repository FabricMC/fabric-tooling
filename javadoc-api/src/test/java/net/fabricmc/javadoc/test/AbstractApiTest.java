package net.fabricmc.javadoc.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

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

public abstract class AbstractApiTest {
	private static final Gson GSON = new Gson();

	@TempDir
	private static Path tempDir;

	protected static Config config;

	protected ApiServer server;
	protected Javalin app;
	protected HttpClient client;

	@BeforeAll
	static void setUpClass() throws Exception {
		KeyPairPath keyPair = generateJWTKeyPair();
		String str = readResource("config.json")
				.replace("%private_key%", keyPair.privateKey.toString())
				.replace("%public_key%", keyPair.publicKey.toString());
		config = Config.parse(str);
	}

	@BeforeEach
	void setUp() {
		server = new ApiServer(config);

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

	private static KeyPairPath generateJWTKeyPair() throws Exception {
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
		kpg.initialize(new ECGenParameterSpec("secp384r1"));
		KeyPair keyPair = kpg.generateKeyPair();

		byte[] privateDer = keyPair.getPrivate().getEncoded();
		Path privateKey = Files.write(tempDir.resolve("private_key.der"), privateDer);

		byte[] publicDer = keyPair.getPublic().getEncoded();
		Path publicKey = Files.write(tempDir.resolve("public_key.der"), publicDer);

		return new KeyPairPath(publicKey, privateKey);
	}

	private record KeyPairPath(Path publicKey, Path privateKey) {}

	private static String readResource(String name) {
		try (InputStream is = AbstractApiTest.class.getClassLoader().getResourceAsStream(name)) {
			return new String(is.readAllBytes());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
