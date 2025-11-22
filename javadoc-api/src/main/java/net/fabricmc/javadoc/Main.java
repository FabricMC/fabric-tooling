package net.fabricmc.javadoc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.javadoc.api.ApiServer;
import net.fabricmc.javadoc.thirdparty.ExternalApis;
import net.fabricmc.javadoc.thirdparty.GithubAPIImpl;

public class Main {
	public static void main(String[] args) throws IOException {
		Config config = Config.parse(Files.readString(Path.of("config.json")));

		ExternalApis externalApis = new ExternalApis(new GithubAPIImpl());
		var apiServer = new ApiServer(config, externalApis);
		apiServer.run();
	}
}
