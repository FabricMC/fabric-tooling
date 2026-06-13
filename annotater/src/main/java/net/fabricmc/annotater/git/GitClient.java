package net.fabricmc.annotater.git;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Minimal Git operations needed by the javadoc database.
 */
public interface GitClient {
	boolean isRepository() throws IOException;

	void addAll(Path path) throws IOException;

	boolean hasStagedChanges(Path path) throws IOException;

	void commit(String message, String authorName, String authorEmail) throws IOException;
}
