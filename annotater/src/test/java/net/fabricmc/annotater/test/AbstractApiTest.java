package net.fabricmc.annotater.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.util.RateLimitUtil;
import io.javalin.testtools.HttpClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import net.fabricmc.annotater.Config;
import net.fabricmc.annotater.api.ApiServer;
import net.fabricmc.annotater.auth.AuthPlatform;
import net.fabricmc.annotater.auth.PermissionGroup;
import net.fabricmc.annotater.thirdparty.ExternalApis;
import net.fabricmc.annotater.thirdparty.GithubAPI;

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
				.replace("%private_key%", keyPair.privateKey.toString().replace("\\", "\\\\"))
				.replace("%public_key%", keyPair.publicKey.toString()).replace("\\", "\\\\");
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

	protected void withAccessToken(Request.Builder builder) {
		JWTCreator.Builder jwt = JWT.create()
				.withIssuer(config.jwt().issuer())
				.withExpiresAt(Instant.now().plus(Duration.ofMinutes(1)))
				.withJWTId(UUID.randomUUID().toString())
				.withClaim("role", PermissionGroup.USER.name().toLowerCase(Locale.ROOT))
				.withClaim("plt", AuthPlatform.GITHUB.name().toLowerCase(Locale.ROOT))
				.withClaim("userid", 0)
				.withSubject("Test")
				.withClaim("type", "access");

		String accessToken = jwt.sign(config.jwt().algorithm());
		builder.addHeader("Authorization", "Bearer " + accessToken);
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
