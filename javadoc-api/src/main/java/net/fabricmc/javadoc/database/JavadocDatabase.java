package net.fabricmc.javadoc.database;

import java.util.Map;

import org.jspecify.annotations.Nullable;

public interface JavadocDatabase {
	@Nullable
	JavadocClassEntry getJavadoc(String className);

	void setClassJavadoc(String className, @Nullable String documentation);

	void setMethodJavadoc(String className, String methodName, String methodDescriptor, @Nullable String documentation);

	void setFieldJavadoc(String className, String fieldName, String fieldDescriptor, @Nullable String documentation);

	record JavadocClassEntry(
			String documentation,
			Map<String, String> methods,
			Map<String, String> fields
	) { }
}
