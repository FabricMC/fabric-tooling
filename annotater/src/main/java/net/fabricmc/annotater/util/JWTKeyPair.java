package net.fabricmc.annotater.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DEREncodable;
import java.security.NoSuchAlgorithmException;
import java.security.PEMDecoder;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;

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
		DEREncodable decoded = decodePEM(path);

		if (decoded instanceof ECPrivateKey privateKey) {
			return privateKey;
		}

		throw new IllegalArgumentException("Unsupported PEM type: " + decoded.getClass().getName());
	}

	public static ECPublicKey loadECPublicKey(Path path) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		DEREncodable decoded = decodePEM(path);

		if (decoded instanceof ECPublicKey publicKey) {
			return publicKey;
		}

		throw new IllegalArgumentException("Unsupported PEM type: " + decoded.getClass().getName());
	}

	private static DEREncodable decodePEM(Path path) throws IOException {
		try (InputStream is = Files.newInputStream(path)) {
			return PEMDecoder.of().decode(is);
		}
	}
}
