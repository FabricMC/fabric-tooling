package net.fabricmc.javadoc.api.v1;

import static io.javalin.apibuilder.ApiBuilder.patch;
import static io.javalin.apibuilder.ApiBuilder.post;

import java.util.Map;

import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiExample;
import io.javalin.openapi.OpenApiExampleProperty;
import io.javalin.openapi.OpenApiNullable;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import org.jspecify.annotations.Nullable;

import net.fabricmc.javadoc.api.util.Role;
import net.fabricmc.javadoc.database.JavadocDatabase;

public record JavadocApi(JavadocDatabase javadocDatabase) {
	public EndpointGroup endpoints() {
		return () -> {
			post("{version}", this::getJavadoc, Role.ACCESS_TOKEN);
			patch("{version}", this::updateJavadoc, Role.ACCESS_TOKEN);
		};
	}

	@OpenApi(
			path = "/v1/javadoc/{version}",
			pathParams = {
				@OpenApiParam(name = "version", description = "The Minecraft version", example = "26.1", required = true)
			},
			methods = HttpMethod.POST,
			summary = "Request the Javadoc for a given class and it's inner classes",
			tags = "Auth",
			operationId = "getJavadoc",
			requestBody = @OpenApiRequestBody(
					content = @OpenApiContent(
							from = JavadocRequest.class,
							exampleObjects = {
									@OpenApiExampleProperty(name = "className", value = "net/minecraft/client/Minecraft")
							}
					)
			),
			responses = {
					@OpenApiResponse(
						status = "200",
						content = @OpenApiContent(
								from = JavadocResponse.class,
								exampleObjects = {
										@OpenApiExampleProperty(name = "data", objects = {
												@OpenApiExampleProperty(name = "net/minecraft/client/Minecraft", objects = {
														@OpenApiExampleProperty(name = "value", value = "The main Minecraft client class"),
														@OpenApiExampleProperty(name = "methods", objects = {
																@OpenApiExampleProperty(name = "getInstance()Lnet/minecraft/client/Minecraft;", value = "Returns the singleton instance of the Minecraft client"),
																@OpenApiExampleProperty(name = "getWindow()Lcom/mojang/blaze3d/platform/Window;", value = "Gets the game window")
														}),
														@OpenApiExampleProperty(name = "fields", objects = {
																@OpenApiExampleProperty(name = "instance:Lnet/minecraft/client/Minecraft;", value = "Current frames per second"),
																@OpenApiExampleProperty(name = "fps:I", value = "Gets the game window")
														})
												}),
												@OpenApiExampleProperty(name = "net/minecraft/client/Minecraft$Inner", objects = {
														@OpenApiExampleProperty(name = "value", value = "An inner class of Minecraft"),
												})
										})
								}
						)
					),
					@OpenApiResponse(status = "401", description = "Unauthorized - Missing or invalid auth token"),
					@OpenApiResponse(status = "404", description = "Javadoc not found for the given class")
			}
	)
	private void getJavadoc(Context context) {
		JavadocRequest request = context.bodyAsClass(JavadocRequest.class);

		JavadocDatabase.JavadocClassEntry javadoc = javadocDatabase().getJavadoc(request.className);

		if (javadoc == null) {
			context.status(HttpStatus.NOT_FOUND);
			return;
		}

		JavadocEntry entry = new JavadocEntry(
				javadoc.documentation(),
				javadoc.methods().isEmpty() ? null : javadoc.methods(),
				javadoc.fields().isEmpty() ? null : javadoc.fields()
		);

		// TODO handle inner classes
		context.json(new JavadocResponse(Map.of(request.className, entry)));
	}

	public record JavadocRequest(@OpenApiExample(value = "net/minecraft/client/Minecraft") String className) { }

	public record JavadocResponse(Map<String, JavadocEntry> data) { }

	public record JavadocEntry(
			@Nullable
			@OpenApiNullable
			String value,
			@Nullable
			@OpenApiNullable
			Map<String, String> methods,
			@Nullable
			@OpenApiNullable
			Map<String, String> fields
	) { }

	@OpenApi(
			path = "/v1/javadoc/{version}",
			pathParams = {
				@OpenApiParam(name = "version", description = "The Minecraft version", example = "26.1", required = true)
			},
			methods = HttpMethod.PATCH,
			summary = "Update the Javadoc for a given class, method, or field",
			tags = "Auth",
			operationId = "updateJavadoc",
			requestBody = @OpenApiRequestBody(
					content = @OpenApiContent(
							from = UpdateJavadocRequest.class,
							exampleObjects = {
									@OpenApiExampleProperty(name = "className", value = "net/minecraft/client/Minecraft"),
									@OpenApiExampleProperty(name = "target", objects = {
											@OpenApiExampleProperty(name = "type", value = "method"),
											@OpenApiExampleProperty(name = "name", value = "getInstance"),
											@OpenApiExampleProperty(name = "descriptor", value = "()Lnet/minecraft/client/Minecraft;")
									}),
									@OpenApiExampleProperty(name = "documentation", value = "The documentation string\nin markdown format.")
							}
					)
			),
			responses = {
				@OpenApiResponse(status = "401", description = "Unauthorized - Missing or invalid auth token"),
				@OpenApiResponse(status = "200", description = "Javadoc updated successfully"),
			}
	)
	private void updateJavadoc(Context context) {
		UpdateJavadocRequest request = context.bodyAsClass(UpdateJavadocRequest.class);

		if (request.target() == null) {
			javadocDatabase().setClassJavadoc(request.className(), request.documentation());
		} else if (request.target().type().equals("method")) {
			javadocDatabase().setMethodJavadoc(
					request.className(),
					request.target().name(),
					request.target().descriptor(),
					request.documentation()
			);
		} else if (request.target().type().equals("field")) {
			javadocDatabase().setFieldJavadoc(
					request.className(),
					request.target().name(),
					request.target().descriptor(),
					request.documentation()
			);
		} else {
			context.status(HttpStatus.BAD_REQUEST).result("Invalid target type: " + request.target().type());
		}
	}

	public record UpdateJavadocRequest(
			String className,
			@Nullable
			@OpenApiNullable
			UpdateTarget target,
			String documentation
	) { }

	public record UpdateTarget(
			String type,
			String name,
			String descriptor
	) { }
}
