package net.fabricmc.javadoc.test.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.HttpStatus;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.fabricmc.javadoc.database.JavadocDatabase;
import net.fabricmc.javadoc.test.AbstractApiTest;

public class JavadocApiTest extends AbstractApiTest {
	@BeforeEach
	void setUpTestData() {
		// Set up test data for each test
		server.getJavadocDatabase().setClassJavadoc("net/minecraft/client/Minecraft", "The main Minecraft client class");
		server.getJavadocDatabase().setMethodJavadoc("net/minecraft/client/Minecraft", "getInstance", "()Lnet/minecraft/client/Minecraft;", "Returns the singleton instance of the Minecraft client");
		server.getJavadocDatabase().setMethodJavadoc("net/minecraft/client/Minecraft", "getWindow", "()Lcom/mojang/blaze3d/platform/Window;", "Gets the game window");
		server.getJavadocDatabase().setFieldJavadoc("net/minecraft/client/Minecraft", "instance", "Lnet/minecraft/client/Minecraft;", "The singleton instance");
		server.getJavadocDatabase().setFieldJavadoc("net/minecraft/client/Minecraft", "fps", "I", "Current frames per second");

		// Set up inner class
		server.getJavadocDatabase().setClassJavadoc("net/minecraft/client/Minecraft$Inner", "An inner class of Minecraft");
	}

	@Test
	void getJavadocSuccess() throws Exception {
		JavadocRequest request = new JavadocRequest("net/minecraft/client/Minecraft");
		Response response = client.post("/v1/javadoc/26.1", request, this::withAccessToken);

		assertStatus(HttpStatus.OK, response);

		String body = response.body().string();
		assertNotNull(body);

		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		assertTrue(json.has("data"));

		JsonObject data = json.getAsJsonObject("data");
		assertTrue(data.has("net/minecraft/client/Minecraft"));

		JsonObject classData = data.getAsJsonObject("net/minecraft/client/Minecraft");
		assertEquals("The main Minecraft client class", classData.get("value").getAsString());

		// Check methods
		assertTrue(classData.has("methods"));
		JsonObject methods = classData.getAsJsonObject("methods");
		assertEquals("Returns the singleton instance of the Minecraft client",
				methods.get("getInstance()Lnet/minecraft/client/Minecraft;").getAsString());
		assertEquals("Gets the game window",
				methods.get("getWindow()Lcom/mojang/blaze3d/platform/Window;").getAsString());

		// Check fields
		assertTrue(classData.has("fields"));
		JsonObject fields = classData.getAsJsonObject("fields");
		assertEquals("The singleton instance",
				fields.get("instanceLnet/minecraft/client/Minecraft;").getAsString());
		assertEquals("Current frames per second",
				fields.get("fpsI").getAsString());
	}

	@Test
	void getJavadocNotFound() {
		JavadocRequest request = new JavadocRequest("net/minecraft/client/NonExistent");
		Response response = client.post("/v1/javadoc/26.1", request, this::withAccessToken);

		assertStatus(HttpStatus.NOT_FOUND, response);
	}

	@Test
	void getJavadocUnauthorized() {
		JavadocRequest request = new JavadocRequest("net/minecraft/client/Minecraft");
		Response response = client.post("/v1/javadoc/26.1", request);

		assertStatus(HttpStatus.UNAUTHORIZED, response);
	}

	@Test
	void updateClassJavadocSuccess() throws Exception {
		String newDoc = "Updated documentation for the Minecraft client class";
		UpdateJavadocRequest request = new UpdateJavadocRequest(
				"net/minecraft/client/TestClass",
				null,
				newDoc
		);

		Response response = client.patch("/v1/javadoc/26.1", request, this::withAccessToken);
		assertStatus(HttpStatus.OK, response);

		// Verify the update
		JavadocDatabase.JavadocClassEntry entry = server.getJavadocDatabase().getJavadoc("net/minecraft/client/TestClass");
		assertNotNull(entry);
		assertEquals(newDoc, entry.documentation());
	}

	@Test
	void updateMethodJavadocSuccess() throws Exception {
		String newDoc = "Updated method documentation";
		UpdateJavadocRequest request = new UpdateJavadocRequest(
				"net/minecraft/client/Minecraft",
				new UpdateTarget("method", "run", "()V"),
				newDoc
		);

		Response response = client.patch("/v1/javadoc/26.1", request, this::withAccessToken);
		assertStatus(HttpStatus.OK, response);

		// Verify the update
		JavadocDatabase.JavadocClassEntry entry = server.getJavadocDatabase().getJavadoc("net/minecraft/client/Minecraft");
		assertNotNull(entry);
		assertTrue(entry.methods().containsKey("run()V"));
		assertEquals(newDoc, entry.methods().get("run()V"));
	}

	@Test
	void updateFieldJavadocSuccess() throws Exception {
		String newDoc = "Updated field documentation";
		UpdateJavadocRequest request = new UpdateJavadocRequest(
				"net/minecraft/client/Minecraft",
				new UpdateTarget("field", "running", "Z"),
				newDoc
		);

		Response response = client.patch("/v1/javadoc/26.1", request, this::withAccessToken);
		assertStatus(HttpStatus.OK, response);

		// Verify the update
		JavadocDatabase.JavadocClassEntry entry = server.getJavadocDatabase().getJavadoc("net/minecraft/client/Minecraft");
		assertNotNull(entry);
		assertTrue(entry.fields().containsKey("runningZ"));
		assertEquals(newDoc, entry.fields().get("runningZ"));
	}

	@Test
	void updateJavadocInvalidTargetType() throws Exception {
		UpdateJavadocRequest request = new UpdateJavadocRequest(
				"net/minecraft/client/Minecraft",
				new UpdateTarget("invalid", "test", "()V"),
				"Some documentation"
		);

		Response response = client.patch("/v1/javadoc/26.1", request, this::withAccessToken);
		assertStatus(HttpStatus.BAD_REQUEST, response);

		String body = response.body().string();
		assertTrue(body.contains("Invalid target type"));
	}

	@Test
	void updateJavadocUnauthorized() {
		UpdateJavadocRequest request = new UpdateJavadocRequest(
				"net/minecraft/client/Minecraft",
				null,
				"New documentation"
		);

		Response response = client.patch("/v1/javadoc/26.1", request);
		assertStatus(HttpStatus.UNAUTHORIZED, response);
	}

	@Test
	void updateAndRetrieveJavadoc() throws Exception {
		// Update class javadoc
		String classDoc = "Test class documentation";
		UpdateJavadocRequest updateRequest = new UpdateJavadocRequest(
				"net/minecraft/test/NewClass",
				null,
				classDoc
		);
		Response updateResponse = client.patch("/v1/javadoc/26.1", updateRequest, this::withAccessToken);
		assertStatus(HttpStatus.OK, updateResponse);

		// Retrieve and verify
		JavadocRequest getRequest = new JavadocRequest("net/minecraft/test/NewClass");
		Response getResponse = client.post("/v1/javadoc/26.1", getRequest, this::withAccessToken);
		assertStatus(HttpStatus.OK, getResponse);

		String body = getResponse.body().string();
		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		JsonObject data = json.getAsJsonObject("data");
		JsonObject classData = data.getAsJsonObject("net/minecraft/test/NewClass");

		assertEquals(classDoc, classData.get("value").getAsString());
	}

	@Test
	void getJavadocWithNoFieldsOrMethods() throws Exception {
		// Create a class with only class documentation
		server.getJavadocDatabase().setClassJavadoc("net/minecraft/test/SimpleClass", "Simple class");

		JavadocRequest request = new JavadocRequest("net/minecraft/test/SimpleClass");
		Response response = client.post("/v1/javadoc/26.1", request, this::withAccessToken);

		assertStatus(HttpStatus.OK, response);

		String body = response.body().string();
		JsonObject json = JsonParser.parseString(body).getAsJsonObject();
		JsonObject data = json.getAsJsonObject("data");
		JsonObject classData = data.getAsJsonObject("net/minecraft/test/SimpleClass");

		assertEquals("Simple class", classData.get("value").getAsString());
		// Methods and fields should not be present in the JSON if they're empty
		assertTrue(!classData.has("methods") || classData.get("methods").isJsonNull());
		assertTrue(!classData.has("fields") || classData.get("fields").isJsonNull());
	}

	@Test
	void deleteClassJavadoc() throws Exception {
		// First add documentation
		server.getJavadocDatabase().setClassJavadoc("net/minecraft/test/ToDelete", "Will be deleted");

		// Delete by setting to null
		UpdateJavadocRequest request = new UpdateJavadocRequest(
				"net/minecraft/test/ToDelete",
				null,
				null
		);
		Response response = client.patch("/v1/javadoc/26.1", request, this::withAccessToken);
		assertStatus(HttpStatus.OK, response);

		// Verify deletion
		JavadocDatabase.JavadocClassEntry entry = server.getJavadocDatabase().getJavadoc("net/minecraft/test/ToDelete");
		assertNull(entry);
	}

	@Test
	void deleteMethodJavadoc() throws Exception {
		// First add method documentation
		server.getJavadocDatabase().setMethodJavadoc("net/minecraft/test/Class", "method", "()V", "Method doc");

		// Delete by setting to null
		UpdateJavadocRequest request = new UpdateJavadocRequest(
				"net/minecraft/test/Class",
				new UpdateTarget("method", "method", "()V"),
				null
		);
		Response response = client.patch("/v1/javadoc/26.1", request, this::withAccessToken);
		assertStatus(HttpStatus.OK, response);

		// Verify deletion
		JavadocDatabase.JavadocClassEntry entry = server.getJavadocDatabase().getJavadoc("net/minecraft/test/Class");
		assertNull(entry);
	}

	@Test
	void deleteFieldJavadoc() throws Exception {
		// First add field documentation
		server.getJavadocDatabase().setFieldJavadoc("net/minecraft/test/Class", "field", "I", "Field doc");

		// Delete by setting to null
		UpdateJavadocRequest request = new UpdateJavadocRequest(
				"net/minecraft/test/Class",
				new UpdateTarget("field", "field", "I"),
				null
		);
		Response response = client.patch("/v1/javadoc/26.1", request, this::withAccessToken);
		assertStatus(HttpStatus.OK, response);

		// Verify deletion
		JavadocDatabase.JavadocClassEntry entry = server.getJavadocDatabase().getJavadoc("net/minecraft/test/Class");
		assertNull(entry);
	}

	record JavadocRequest(String className) { }

	record UpdateJavadocRequest(String className, UpdateTarget target, String documentation) { }

	record UpdateTarget(String type, String name, String descriptor) { }
}
