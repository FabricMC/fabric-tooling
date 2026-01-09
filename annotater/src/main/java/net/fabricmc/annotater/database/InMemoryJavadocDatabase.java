package net.fabricmc.annotater.database;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

// TODO replace with persistent database implementation, this is just for testing
public class InMemoryJavadocDatabase implements JavadocDatabase {
	private final Map<String, String> classDocumentation = new ConcurrentHashMap<>();
	private final Map<String, Map<String, String>> methodDocumentation = new ConcurrentHashMap<>();
	private final Map<String, Map<String, String>> fieldDocumentation = new ConcurrentHashMap<>();

	@Override
	@Nullable
	public JavadocClassEntry getJavadoc(String className) {
		String classDoc = classDocumentation.get(className);
		Map<String, String> methods = methodDocumentation.getOrDefault(className, Map.of());
		Map<String, String> fields = fieldDocumentation.getOrDefault(className, Map.of());

		if (classDoc == null && methods.isEmpty() && fields.isEmpty()) {
			return null;
		}

		return new JavadocClassEntry(classDoc, new HashMap<>(methods), new HashMap<>(fields));
	}

	@Override
	public void setClassJavadoc(String className, @Nullable String documentation) {
		if (documentation != null) {
			classDocumentation.put(className, documentation);
		} else {
			classDocumentation.remove(className);
		}
	}

	@Override
	public void setMethodJavadoc(String className, String methodName, String methodDescriptor, @Nullable String documentation) {
		String key = methodName + methodDescriptor;

		if (documentation != null) {
			methodDocumentation.computeIfAbsent(className, k -> new ConcurrentHashMap<>()).put(key, documentation);
		} else {
			Map<String, String> methods = methodDocumentation.get(className);

			if (methods != null) {
				methods.remove(key);

				if (methods.isEmpty()) {
					methodDocumentation.remove(className);
				}
			}
		}
	}

	@Override
	public void setFieldJavadoc(String className, String fieldName, String fieldDescriptor, @Nullable String documentation) {
		String key = fieldName + fieldDescriptor;

		if (documentation != null) {
			fieldDocumentation.computeIfAbsent(className, k -> new ConcurrentHashMap<>()).put(key, documentation);
		} else {
			Map<String, String> fields = fieldDocumentation.get(className);

			if (fields != null) {
				fields.remove(key);

				if (fields.isEmpty()) {
					fieldDocumentation.remove(className);
				}
			}
		}
	}
}
