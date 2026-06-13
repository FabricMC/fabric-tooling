package net.fabricmc.annotater.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CliGitClient implements GitClient {
	private final Path repository;

	public CliGitClient(Path repository) {
		this.repository = repository.toAbsolutePath().normalize();
	}

	@Override
	public boolean isRepository() throws IOException {
		if (!Files.isDirectory(repository)) {
			return false;
		}

		CommandResult result = runAllowingFailure("rev-parse", "--is-inside-work-tree");
		return result.exitCode() == 0 && result.output().trim().equals("true");
	}

	@Override
	public void addAll(Path path) throws IOException {
		run("add", "-A", "--", repository.relativize(path).toString());
	}

	@Override
	public boolean hasStagedChanges(Path path) throws IOException {
		CommandResult result = runAllowingFailure("diff", "--cached", "--quiet", "--", repository.relativize(path).toString());

		if (result.exitCode() == 0) {
			return false;
		} else if (result.exitCode() == 1) {
			return true;
		}

		throw commandFailed(result, "diff", "--cached", "--quiet");
	}

	@Override
	public void commit(String message, String authorName, String authorEmail) throws IOException {
		String author = authorName + " <" + authorEmail + ">";
		run(
				"-c", "user.name=" + authorName,
				"-c", "user.email=" + authorEmail,
				"commit",
				"--author", author,
				"-m", message
		);
	}

	private CommandResult run(String... args) throws IOException {
		CommandResult result = runAllowingFailure(args);

		if (result.exitCode() != 0) {
			throw commandFailed(result, args);
		}

		return result;
	}

	private CommandResult runAllowingFailure(String... args) throws IOException {
		List<String> command = new ArrayList<>();
		command.add("git");
		command.addAll(List.of(args));

		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.directory(repository.toFile());
		processBuilder.redirectErrorStream(true);

		try {
			Process process = processBuilder.start();
			byte[] output = process.getInputStream().readAllBytes();
			int exitCode = process.waitFor();

			return new CommandResult(exitCode, new String(output, StandardCharsets.UTF_8));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while running " + String.join(" ", command), e);
		}
	}

	private static IOException commandFailed(CommandResult result, String... args) {
		return new IOException("Git command failed with exit code " + result.exitCode()
				+ ": git " + String.join(" ", args)
				+ "\noutput:\n" + result.output());
	}

	private record CommandResult(int exitCode, String output) { }
}
