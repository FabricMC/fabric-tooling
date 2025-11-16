package net.fabricmc.javadoc.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

// Just for testing! Do not use this to generate prod keys without first checking it over.
public class KeyGen {
	public static void main(String[] args) throws Exception {
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
		kpg.initialize(new ECGenParameterSpec("secp384r1"));
		KeyPair keyPair = kpg.generateKeyPair();

		byte[] privateDer = keyPair.getPrivate().getEncoded();
		writePemFile(Path.of("private_key.pem"), "PRIVATE KEY", privateDer);

		byte[] publicDer = keyPair.getPublic().getEncoded();
		writePemFile(Path.of("public_key.pem"), "PUBLIC KEY", publicDer);
	}

	private static void writePemFile(Path file, String type, byte[] derBytes) throws IOException {
		String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(derBytes);

		String pem = "-----BEGIN " + type + "-----\n"
				+ base64
				+ "\n-----END " + type + "-----\n";

		Files.write(file, pem.getBytes());
	}
}
