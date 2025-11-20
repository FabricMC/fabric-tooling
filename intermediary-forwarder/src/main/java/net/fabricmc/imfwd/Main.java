package net.fabricmc.imfwd;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.stream.JsonReader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Handles requests to intermediary files on Fabric's Maven server as a 404 fallback by redirecting them to the proper
 * intermediary version as determined by the Fabric Meta service.
 */
public class Main {
	private static final Pattern REQUEST_PATTERN = Pattern.compile("/net/fabricmc/intermediary/([^/]{1,50})/intermediary-\\1\\.([\\.\\w]{1,20})");

	private static final String RESPONSE = "<html><head><title>ERR</title></head><body><center><h1>ERR</h1></center></body></html>\n";
	private static final byte[] RESPONSE_302 = RESPONSE.replace("ERR", "302 Found").getBytes(StandardCharsets.UTF_8);
	private static final byte[] RESPONSE_404 = RESPONSE.replace("ERR", "404 Not Found").getBytes(StandardCharsets.UTF_8);
	private static final byte[] RESPONSE_405 = RESPONSE.replace("ERR", "405 Not Allowed").getBytes(StandardCharsets.UTF_8);
	private static final byte[] RESPONSE_500 = RESPONSE.replace("ERR", "500 Internal Server Error").getBytes(StandardCharsets.UTF_8);

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	@SuppressWarnings("serial")
	private static final Map<String, CacheEntry> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(500) {
		protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
			return size() >= 500;
		}
	});

	private static final Map<String, Instant> RELEASE_TIMES = new HashMap<>();
	private static long lastReleaseTimeUpdate;

	private static String metaScheme;
	private static String metaHost;
	private static int metaPort;

	private static String mavenScheme;
	private static String mavenHost;
	private static int mavenPort;

	public static void main(String[] args) throws IOException {
		if (args.length != 2) throw new IllegalArgumentException("usage: <metaUrl> <mavenUrl>"); // e.g. https://meta2.fabricmc.net https://maven.fabricmc.net

		URI metaUrl = URI.create(args[0]);
		metaScheme = metaUrl.getScheme();
		metaHost = metaUrl.getHost();
		metaPort = metaUrl.getPort();

		URI mavenUrl = URI.create(args[1]);
		mavenScheme = mavenUrl.getScheme();
		mavenHost = mavenUrl.getHost();
		mavenPort = mavenUrl.getPort();

		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 13694), 0);
		server.createContext("/net/fabricmc/intermediary/", Main::handleIntermediary);
		server.start();

		System.out.println("intermediary forwarder running");
	}

	private static void handleIntermediary(HttpExchange exchange) throws IOException {
		//System.out.println("new request: "+exchange.getRequestURI());

		try (exchange) {
			// consume extraneous request data
			exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());

			String method = exchange.getRequestMethod();
			Matcher matcher;
			CacheEntry entry;
			int result;

			if (!method.equals("GET")
					&& !method.equals("HEAD")) { // bad request method
				exchange.getResponseHeaders().add("Allow", "HEAD, GET");
				result = 405;
			} else if (!(matcher = REQUEST_PATTERN.matcher(exchange.getRequestURI().getRawPath())).matches()
					|| (entry = getNewIntermediary(matcher.group(1))) != null && entry.isAbsent()) { // would-be 404 requests
				result = 404;
			} else if (entry == null) { // replacement fetch error
				result = 500;
			} else { // replacement success
				String newLocation = String.format("/net/fabricmc/intermediary/%s/intermediary-%<s.%s", entry.newVersion(), matcher.group(2));
				//System.out.println("match: "+matcher.group(1)+" -> "+newLocation);
				exchange.getResponseHeaders().add("Location", newLocation);
				exchange.getResponseHeaders().add("Last-Modified", entry.lastModified());
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

			//System.out.println("sent response: "+result);
		}
	}

	/**
	 * Get the proper intermediary version from the meta server.
	 *
	 * @param version intermediary version to check
	 * @return replacement version, "" if none or null on error
	 */
	private static CacheEntry getNewIntermediary(String version) {
		CacheEntry ret = CACHE.get(version);

		if (ret != null && !ret.isExpired()) {
			return ret;
		}

		return computeNewIntermediary(version);
	}

	private static synchronized CacheEntry computeNewIntermediary(String version) {
		CacheEntry ret = CACHE.get(version);

		if (ret != null && !ret.isExpired()) { // repeated due to synchronization/concurrency
			return ret;
		}

		try {
			URI uri = new URI(metaScheme, null, metaHost, metaPort, "/v2/versions/intermediary/"+version, null, null);
			//System.out.println("querying meta: "+uri);
			HttpRequest request = HttpRequest.newBuilder(uri)
					.timeout(Duration.ofSeconds(5))
					.build();
			HttpResponse<String> response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
			//System.out.println("response "+response.statusCode()+": "+response.body());
			if (response.statusCode() != 200) return null;

			String newVersion;

			if (response.body().startsWith("[]")) {
				ret = CacheEntry.ofAbsent();
			} else if ((newVersion = parseVersion(response.body())) == null) { // empty or invalid result
				return null;
			} else if (newVersion.equals(version)) { // identical result, keep original 404 (meta knows version, maven doesn't have it)
				ret = CacheEntry.ofAbsent();
			} else {
				Instant releaseTime = getReleaseTime(version);
				if (releaseTime == null) return null;

				ret = CacheEntry.of(newVersion, releaseTime);
			}

			assert !ret.isExpired();
			CACHE.put(version, ret);

			return ret;
		} catch (IOException | InterruptedException | URISyntaxException e) {
			System.out.println("meta request failed: "+e);
			return null;
		}
	}

	private record CacheEntry(String newVersion, String lastModified, long time) {
		static CacheEntry of(String newVersion, Instant releaseTime) {
			return new CacheEntry(newVersion, DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH).withZone(ZoneId.of("GMT")).format(releaseTime), System.nanoTime());
		}

		static CacheEntry ofAbsent() {
			return new CacheEntry(null, null, System.nanoTime());
		}

		boolean isAbsent() {
			return newVersion == null;
		}

		boolean isExpired() {
			return isAbsent() && System.nanoTime() - time > 60_000_000_000L; // negative (absent) entries expire after 60s
		}
	}

	private static String parseVersion(String metaVersionJson) {
		try (JsonReader reader = new JsonReader(new StringReader(metaVersionJson))) {
			reader.beginArray();

			if (!reader.hasNext()) return null;

			reader.beginObject();

			while (reader.hasNext()) {
				switch (reader.nextName()) {
				case "version": return reader.nextString();
				default: reader.skipValue();
				}
			}

			reader.endObject();

			reader.endArray();
		} catch (Exception e) {
			System.out.println("meta response parsing failed: "+e);
		}

		return null;
	}

	private static synchronized Instant getReleaseTime(String version) {
		Instant ret = RELEASE_TIMES.get(version);
		if (ret != null) return ret;

		long time = System.nanoTime();

		if (lastReleaseTimeUpdate != 0
				&& time - lastReleaseTimeUpdate < 30_000_000_000L) { // only allow one request every 30s
			return null;
		}

		lastReleaseTimeUpdate = time; // update even if we fail later

		Map<String, Instant> mcMetaResult = fetchReleaseTimes(URI.create("https://piston-meta.mojang.com/mc/game/version_manifest.json"));
		Map<String, Instant> fabricExpResult;

		try {
			fabricExpResult = fetchReleaseTimes(new URI(mavenScheme, null, mavenHost, mavenPort, "/net/minecraft/experimental_versions.json", null, null));
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}

		if (mcMetaResult == null || fabricExpResult == null) return null;

		RELEASE_TIMES.clear();
		RELEASE_TIMES.putAll(fabricExpResult);
		RELEASE_TIMES.putAll(mcMetaResult);

		return RELEASE_TIMES.get(version);
	}

	private static Map<String, Instant> fetchReleaseTimes(URI url) {
		try {
			HttpRequest request = HttpRequest.newBuilder(url)
					.timeout(Duration.ofSeconds(5))
					.build();

			HttpResponse<String> response = HTTP_CLIENT.send(request, BodyHandlers.ofString());

			if (response.statusCode() != 200) {
				throw new IOException("http request failed: "+response.statusCode());
			}

			Map<String, Instant> ret = new HashMap<>();

			// parse mcmeta version manifest to extract release times
			try (JsonReader reader = new JsonReader(new StringReader(response.body()))) {
				reader.beginObject();

				while (reader.hasNext()) {
					String key = reader.nextName();

					if (!key.equals("versions")) {
						reader.skipValue();
						continue;
					}

					reader.beginArray();

					while (reader.hasNext()) {
						String id = null;
						String releaseTime = null;

						reader.beginObject();

						while (reader.hasNext()) {
							switch (reader.nextName()) {
							case "id" -> id = reader.nextString();
							case "releaseTime" -> releaseTime = reader.nextString();
							default -> reader.skipValue();
							}
						}

						reader.endObject();

						if (id == null || releaseTime == null) {
							throw new IOException("missing id or releaseTime in version entry");
						}

						ret.put(id, Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(releaseTime)));
					}

					reader.endArray();
				}

				reader.endObject();
			}

			return ret;
		} catch (Exception e) {
			System.out.println("maven manifest request failed: "+e);

			return null;
		}
	}
}
