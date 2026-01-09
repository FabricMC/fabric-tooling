package net.fabricmc.annotater.api;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import org.jetbrains.annotations.VisibleForTesting;

import net.fabricmc.annotater.Config;
import net.fabricmc.annotater.api.v1.AuthApi;
import net.fabricmc.annotater.api.v1.JavadocApi;
import net.fabricmc.annotater.api.v1.RoleAuthenticationHandler;
import net.fabricmc.annotater.auth.impl.AccessTokenControllerImpl;
import net.fabricmc.annotater.auth.impl.RefreshTokenControllerImpl;
import net.fabricmc.annotater.auth.oauth.GithubOAuthProvider;
import net.fabricmc.annotater.database.InMemoryJavadocDatabase;
import net.fabricmc.annotater.database.JavadocDatabase;
import net.fabricmc.annotater.thirdparty.ExternalApis;

public class ApiServer {
	private final Javalin app;
	private final JavadocDatabase javadocDatabase;

	public ApiServer(Config appConfig, ExternalApis externalApis) {
		var refreshTokenController = new RefreshTokenControllerImpl(appConfig);
		var accessTokenController = new AccessTokenControllerImpl(appConfig);
		var githubOAuthProvider = new GithubOAuthProvider(appConfig, externalApis.github());
		var roleAuthenticationHandler = new RoleAuthenticationHandler(refreshTokenController, accessTokenController);
		var authApi = new AuthApi(appConfig, accessTokenController, refreshTokenController, githubOAuthProvider);
		this.javadocDatabase = new InMemoryJavadocDatabase();
		var javadocApi = new JavadocApi(this.javadocDatabase);

		this.app = Javalin.create(config -> {
			config.showJavalinBanner = false;

			config.router.mount(routing -> {
				routing.beforeMatched("/v1/*", roleAuthenticationHandler);
			}).apiBuilder(() -> {
				path("/v1", () -> {
					path("/auth", authApi.endpoints());
					path("/javadoc", javadocApi.endpoints());
				});

				get("/", this::handleRoot);
			});
		});
	}

	public void run() {
		app.start(8080);
	}

	private void handleRoot(Context context) {
		context.contentType(ContentType.HTML);
		context.result(readResource("index.html"));
	}

	@VisibleForTesting
	public Javalin getApp() {
		return app;
	}

	@VisibleForTesting
	public JavadocDatabase getJavadocDatabase() {
		return javadocDatabase;
	}

	private static String readResource(String name) {
		try (InputStream is = ApiServer.class.getClassLoader().getResourceAsStream(name)) {
			return new String(is.readAllBytes());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
