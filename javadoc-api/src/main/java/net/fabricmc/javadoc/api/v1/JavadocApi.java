package net.fabricmc.javadoc.api.v1;

import static io.javalin.apibuilder.ApiBuilder.patch;
import static io.javalin.apibuilder.ApiBuilder.post;

import java.util.Map;

import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
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

	public record JavadocRequest(String className) { }

	public record JavadocResponse(Map<String, JavadocEntry> data) { }

	public record JavadocEntry(
			@Nullable
			String value,
			@Nullable
			Map<String, String> methods,
			@Nullable
			Map<String, String> fields
	) { }

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
			UpdateTarget target,
			String documentation
	) { }

	public record UpdateTarget(
			String type,
			String name,
			String descriptor
	) { }
}
