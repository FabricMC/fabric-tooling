package net.fabricmc.javadoc.api;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.redoc.ReDocPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import org.jetbrains.annotations.VisibleForTesting;

import net.fabricmc.javadoc.api.v1.AuthApi;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;


public class ApiServer {
	private final Javalin app;

	public ApiServer(AuthApi authApi) {
		this.app = Javalin.create(config -> {
			config.showJavalinBanner = false;

			config.registerPlugin(new OpenApiPlugin(openApi -> {
				openApi.withDefinitionConfiguration((version, definition) -> {
					definition.withInfo(openApiInfo -> {
						openApiInfo.title("Javadoc API docs");
					});
				});
			}));
			config.registerPlugin(new SwaggerPlugin());
			config.registerPlugin(new ReDocPlugin());

			config.router.mount(routing -> {
				routing.beforeMatched("/v1/*", authApi::handleAccess);
			}).apiBuilder(() -> {
				path("/v1", () -> {
					path("/auth", authApi.endpoints());
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

	private static String readResource(String name) {
		try (InputStream is = ApiServer.class.getClassLoader().getResourceAsStream(name)) {
			return new String(is.readAllBytes());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
