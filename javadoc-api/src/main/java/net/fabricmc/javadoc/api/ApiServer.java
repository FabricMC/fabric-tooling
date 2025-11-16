package net.fabricmc.javadoc.api;

import static io.javalin.apibuilder.ApiBuilder.path;

import io.javalin.Javalin;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.redoc.ReDocPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;

import net.fabricmc.javadoc.api.v1.AuthApi;


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
			});
		});
	}

	public void run() {
		app.start(8080);
	}
}
