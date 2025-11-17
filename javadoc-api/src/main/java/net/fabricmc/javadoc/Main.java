package net.fabricmc.javadoc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.javadoc.api.ApiServer;

public class Main {
	public static void main(String[] args) throws IOException {
		Config config = Config.parse(Files.readString(Path.of("config.json")));
		var apiServer = new ApiServer(config);
		apiServer.run();
	}
}
