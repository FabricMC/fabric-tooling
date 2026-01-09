package net.fabricmc.annotater.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.ECDSAKeyProvider;

public class JWTKeyPair implements ECDSAKeyProvider {
	private final ECPublicKey publicKey;
	private final ECPrivateKey privateKey;

	public JWTKeyPair(Path publicKey, Path privateKey) {
		try {
			this.publicKey = loadECPublicKey(publicKey);
			this.privateKey = loadPrivateKey(privateKey);
		} catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new RuntimeException("Failed to load JWT key pair", e);
		}
	}

	public Algorithm jwtAlgorithm() {
		return Algorithm.ECDSA384(this);
	}

	@Override
	public ECPublicKey getPublicKeyById(String keyId) {
		return publicKey;
	}

	@Override
	public ECPrivateKey getPrivateKey() {
		return privateKey;
	}

	@Override
	public String getPrivateKeyId() {
		return null;
	}

	// Load an EC private key from a PEM file
	public static ECPrivateKey loadPrivateKey(Path path) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		byte[] keyBytes = Files.readAllBytes(path);

		String pem = new String(keyBytes);

		if (pem.contains("BEGIN")) {
			pem = pem
					.replace("-----BEGIN PRIVATE KEY-----", "")
					.replace("-----END PRIVATE KEY-----", "")
					.replaceAll("\\s", "");
			keyBytes = Base64.getDecoder().decode(pem);
		}

		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
		KeyFactory kf = KeyFactory.getInstance("EC");
		return (ECPrivateKey) kf.generatePrivate(spec);
	}

	public static ECPublicKey loadECPublicKey(Path path) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		byte[] keyBytes = Files.readAllBytes(path);

		String pem = new String(keyBytes);

		if (pem.contains("BEGIN")) {
			pem = pem
					.replace("-----BEGIN PUBLIC KEY-----", "")
					.replace("-----END PUBLIC KEY-----", "")
					.replaceAll("\\s", "");
			keyBytes = Base64.getDecoder().decode(pem);
		}

		X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
		KeyFactory kf = KeyFactory.getInstance("EC");
		return (ECPublicKey) kf.generatePublic(spec);
	}
}
