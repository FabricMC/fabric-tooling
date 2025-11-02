package net.fabricmc.imfwd;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Handles requests to intermediary files on Fabric's Maven server as a 404 fallback by redirecting them to the proper
 * intermediary version as determined by the Fabric Meta service.
 */
public class Main {
	private static final Pattern REQUEST_PATTERN = Pattern.compile("/net/fabricmc/intermediary/([^/]{1,50})/intermediary-\\1\\.([\\.\\w]{1,20})");
	private static final Pattern RESPONSE_PATTERN = Pattern.compile("\"version\": \"([^\"]+)\"");

	private static final String RESPONSE = "<html><head><title>ERR</title></head><body><center><h1>ERR</h1></center></body></html>\n";
	private static final byte[] RESPONSE_302 = RESPONSE.replace("ERR", "302 Found").getBytes(StandardCharsets.UTF_8);
	private static final byte[] RESPONSE_404 = RESPONSE.replace("ERR", "404 Not Found").getBytes(StandardCharsets.UTF_8);
	private static final byte[] RESPONSE_405 = RESPONSE.replace("ERR", "405 Not Allowed").getBytes(StandardCharsets.UTF_8);
	private static final byte[] RESPONSE_500 = RESPONSE.replace("ERR", "500 Internal Server Error").getBytes(StandardCharsets.UTF_8);

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	@SuppressWarnings("serial")
	private static final Map<String, String> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(500) {
		protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
			return size() >= 500;
		}
	});

	private static String metaScheme;
	private static String metaHost;
	private static int metaPort;

	public static void main(String[] args) throws IOException {
		if (args.length != 1) throw new IllegalArgumentException("usage: <metaUrl>"); // e.g. https://meta2.fabricmc.net

		URI metaUrl = URI.create(args[0]);
		metaScheme = metaUrl.getScheme();
		metaHost = metaUrl.getHost();
		metaPort = metaUrl.getPort();

		HttpServer server = HttpServer.create(new InetSocketAddress((InetAddress) null, 13694), 0);
		server.createContext("/net/fabricmc/intermediary/", Main::handleIntermediary);
		server.start();

		System.out.println("intermediary forwarder running");
	}

	private static void handleIntermediary(HttpExchange exchange) throws IOException {
		String method = exchange.getRequestMethod();
		Matcher matcher;
		String newVersion;
		int result;

		if (!method.equals("GET")
				&& !method.equals("HEAD")) { // bad request method
			exchange.getResponseHeaders().add("Allow", "HEAD, GET");
			result = 405;
		} else if (!(matcher = REQUEST_PATTERN.matcher(exchange.getRequestURI().getRawPath())).matches()
				|| "".equals(newVersion = getNewIntermediary(matcher.group(1)))) { // would-be 404 requests
			result = 404;
		} else if (newVersion == null) { // replacement fetch error
			result = 500;
		} else { // replacement success
			String newLocation = String.format("/net/fabricmc/intermediary/%s/intermediary-%<s.%s", newVersion, matcher.group(2));
			//System.out.println("match: "+matcher.group(1)+" -> "+newLocation);
			exchange.getResponseHeaders().add("Location", newLocation);
			result = 302;
		}

		byte[] response = switch (result) {
		case 302 -> RESPONSE_302;
		case 404 -> RESPONSE_404;
		case 405 -> RESPONSE_405;
		case 500 -> RESPONSE_500;
		default -> throw new IllegalStateException();
		};

		if (method.equals("HEAD")) {
			exchange.getResponseHeaders().set("Content-Length", Integer.toString(response.length));
			exchange.sendResponseHeaders(result, -1);
		} else {
			exchange.sendResponseHeaders(result, response.length);
			exchange.getResponseBody().write(response);
		}
	}

	/**
	 * Get the proper intermediary version from the meta server.
	 *
	 * @param version intermediary version to check
	 * @return replacement version, "" if none or null on error
	 */
	private static String getNewIntermediary(String version) {
		String ret = CACHE.get(version);
		if (ret != null) return ret;

		try {
			HttpRequest request = HttpRequest.newBuilder(new URI(metaScheme, null, metaHost, metaPort, "/v2/versions/intermediary/"+version, null, null))
					.timeout(Duration.ofSeconds(5))
					.build();
			HttpResponse<String> response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
			if (response.statusCode() != 200) return null;

			Matcher matcher;

			if (response.body().startsWith("[]")) {
				ret = "";
			} else if (!(matcher = RESPONSE_PATTERN.matcher(response.body())).find()) {
				return null;
			} else {
				ret = matcher.group(1);
				if (ret.equals(version)) ret = ""; // identical result, keep original 404 (meta knows version, maven doesn't have it)
			}

			CACHE.put(version, ret);

			return ret;
		} catch (IOException | InterruptedException | URISyntaxException e) {
			System.out.println("meta request failed: "+e);
			return null;
		}
	}
}
