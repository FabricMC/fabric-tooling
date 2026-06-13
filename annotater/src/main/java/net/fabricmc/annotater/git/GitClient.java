package net.fabricmc.annotater.git;

import java.io.IOException;

/**
 * Minimal Git operations needed by the javadoc database.
 */
public interface GitClient {
	boolean isRepository() throws IOException;

	void addAll() throws IOException;

	boolean hasStagedChanges() throws IOException;

	void commit(String message, String authorName, String authorEmail) throws IOException;
}
