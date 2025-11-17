package net.fabricmc.javadoc;

import java.lang.reflect.Type;
import java.nio.file.Path;

import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import net.fabricmc.javadoc.util.JWTKeyPair;

public record Config(
		// The base url of this API. e.g "https://api.example.com"
		String apiUrl,
		Jwt jwt,
		GithubOAuth githubOAuth
) {
	private static final Gson GSON = new GsonBuilder().registerTypeAdapter(Jwt.class, new Jwt.JwtDeserializer()).create();

	public static Config parse(String json) {
		return GSON.fromJson(json, Config.class);
	}

	public record Jwt(
			// The issuer field for the JWTs issued by this API, e.g "api.example.com"
			String issuer,
			Algorithm algorithm
	) {

		public static class JwtDeserializer implements JsonDeserializer<Jwt> {
			@Override
			public Jwt deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
				var jsonObject = json.getAsJsonObject();
				String issuer = jsonObject.get("issuer").getAsString();
				String publicKey = jsonObject.get("publicKey").getAsString();
				String privateKey = jsonObject.get("privateKey").getAsString();
				return new Jwt(issuer, new JWTKeyPair(Path.of(publicKey), Path.of(privateKey)).jwtAlgorithm());
			}
		}
	}

	public interface OAuth {
		String clientID();
		String clientSecret();
	}

	public record GithubOAuth(
			String clientID,
			String clientSecret
	) implements OAuth {}
}
