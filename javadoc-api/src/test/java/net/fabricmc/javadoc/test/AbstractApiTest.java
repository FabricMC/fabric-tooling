package net.fabricmc.javadoc.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.util.RateLimitUtil;
import io.javalin.testtools.HttpClient;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import net.fabricmc.javadoc.Config;
import net.fabricmc.javadoc.api.ApiServer;
import net.fabricmc.javadoc.thirdparty.ExternalApis;
import net.fabricmc.javadoc.thirdparty.GithubAPI;

public abstract class AbstractApiTest {
	@TempDir
	private static Path tempDir;

	protected static Config config;

	protected GithubAPI mockGithubApi;

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
		mockGithubApi = Mockito.mock(GithubAPI.class);
		var externalApis = new ExternalApis(mockGithubApi);
		server = new ApiServer(config, externalApis);

		app = server.getApp();
		app.start(0);

		client = new HttpClient(app, new OkHttpClient.Builder()
				.followRedirects(false)
				.followSslRedirects(false)
				.build());
	}

	@AfterEach
	void tearDown() {
		app.stop();

		// Reset rate limit
		RateLimitUtil.INSTANCE.getLimiters().clear();
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

	private record KeyPairPath(Path publicKey, Path privateKey) { }

	private static String readResource(String name) {
		try (InputStream is = AbstractApiTest.class.getClassLoader().getResourceAsStream(name)) {
			return new String(is.readAllBytes());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
