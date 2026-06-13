package net.fabricmc.annotater.test.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.fabricmc.annotater.database.GitJavadocDatabase;
import net.fabricmc.annotater.database.JavadocDatabase;

public class GitJavadocDatabaseTest {
	@TempDir
	Path tempDir;

	private Path repository;

	@BeforeEach
	void setUp() throws IOException {
		repository = tempDir.resolve("javadocs");
		Files.createDirectory(repository);
		runGit(repository, "init");
	}

	@Test
	void javadocsRoundTripAfterReload() {
		GitJavadocDatabase database = new GitJavadocDatabase(repository);
		database.setClassJavadoc("net/minecraft/test/TestClass", "Class docs");
		database.setMethodJavadoc("net/minecraft/test/TestClass", "test", "()V", "Method docs");
		database.setFieldJavadoc("net/minecraft/test/TestClass", "field", "I", "Field docs");

		GitJavadocDatabase reloaded = new GitJavadocDatabase(repository);
		JavadocDatabase.JavadocClassEntry entry = reloaded.getJavadoc("net/minecraft/test/TestClass");

		assertNotNull(entry);
		assertEquals("Class docs", entry.documentation());
		assertEquals("Method docs", entry.methods().get("test()V"));
		assertEquals("Field docs", entry.fields().get("fieldI"));
	}

	@Test
	void deletingLastCommentRemovesEntry() {
		GitJavadocDatabase database = new GitJavadocDatabase(repository);
		database.setMethodJavadoc("net/minecraft/test/TestClass", "test", "()V", "Method docs");
		assertNotNull(database.getJavadoc("net/minecraft/test/TestClass"));

		database.setMethodJavadoc("net/minecraft/test/TestClass", "test", "()V", null);

		assertNull(database.getJavadoc("net/minecraft/test/TestClass"));
		assertEquals("2", gitOutput(repository, "rev-list", "--count", "--all").trim());
	}

	@Test
	void eachRealUpdateCreatesOneCommit() {
		GitJavadocDatabase database = new GitJavadocDatabase(repository);

		database.setClassJavadoc("net/minecraft/test/TestClass", "Class docs");
		assertEquals("1", gitOutput(repository, "rev-list", "--count", "--all").trim());

		database.setMethodJavadoc("net/minecraft/test/TestClass", "test", "()V", "Method docs");
		assertEquals("2", gitOutput(repository, "rev-list", "--count", "--all").trim());

		database.setFieldJavadoc("net/minecraft/test/TestClass", "field", "I", "Field docs");
		assertEquals("3", gitOutput(repository, "rev-list", "--count", "--all").trim());
	}

	@Test
	void noOpUpdateDoesNotCreateCommit() {
		GitJavadocDatabase database = new GitJavadocDatabase(repository);
		database.setClassJavadoc("net/minecraft/test/TestClass", "Class docs");
		assertEquals("1", gitOutput(repository, "rev-list", "--count", "--all").trim());

		database.setClassJavadoc("net/minecraft/test/TestClass", "Class docs");

		assertEquals("1", gitOutput(repository, "rev-list", "--count", "--all").trim());
	}

	@Test
	void updateDoesNotCommitUnrelatedRepoFiles() throws IOException {
		Files.writeString(repository.resolve("notes.txt"), "not part of the javadoc database");

		GitJavadocDatabase database = new GitJavadocDatabase(repository);
		database.setClassJavadoc("net/minecraft/test/TestClass", "Class docs");

		assertEquals("1", gitOutput(repository, "rev-list", "--count", "--all").trim());
		assertEquals("?? notes.txt", gitOutput(repository, "status", "--short", "--", "notes.txt").trim());
	}

	@Test
	void innerClassUsesOuterClassMappingFile() throws IOException {
		GitJavadocDatabase database = new GitJavadocDatabase(repository);
		database.setClassJavadoc("net/minecraft/client/Minecraft$Inner", "Inner docs");

		Path outerFile = repository.resolve("mappings/net/minecraft/client/Minecraft.mapping");
		Path innerFile = repository.resolve("mappings/net/minecraft/client/Minecraft$Inner.mapping");

		assertTrue(Files.exists(outerFile));
		assertTrue(Files.notExists(innerFile));
		assertTrue(Files.readString(outerFile).contains("CLASS Inner"));
		assertTrue(Files.readString(outerFile).contains("COMMENT Inner docs"));
	}

	@Test
	void constructorFailsForNonGitDirectory() throws IOException {
		Path nonGitDirectory = tempDir.resolve("not-git");
		Files.createDirectory(nonGitDirectory);

		assertThrows(IllegalArgumentException.class, () -> new GitJavadocDatabase(nonGitDirectory));
	}

	private static void runGit(Path repository, String... args) throws IOException {
		git(repository, args);
	}

	private static String gitOutput(Path repository, String... args) {
		try {
			return git(repository, args);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static String git(Path repository, String... args) throws IOException {
		List<String> command = new ArrayList<>();
		command.add("git");
		command.addAll(List.of(args));

		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.directory(repository.toFile());

		try {
			Process process = processBuilder.start();
			String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
			int exitCode = process.waitFor();

			if (exitCode != 0) {
				throw new IOException("Git command failed with exit code " + exitCode
						+ ": " + String.join(" ", command)
						+ "\nstdout:\n" + stdout
						+ "\nstderr:\n" + stderr);
			}

			return stdout;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while running " + String.join(" ", command), e);
		}
	}
}
