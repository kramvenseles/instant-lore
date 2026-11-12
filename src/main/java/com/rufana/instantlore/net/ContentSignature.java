package com.rufana.instantlore.net;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class ContentSignature {
	private static final String PRIMARY_KEY_BASE64 =
			"MCowBQYDK2VwAyEARd0UYb699UvUDQgsZTyjv2Zg7Yu2qJYwPDfyfA+OO7k=";

	private static final String BACKUP_KEY_BASE64 =
			"MCowBQYDK2VwAyEAl34/kNwBfXrfpOp6r1xFyiOnABh5NnwhA4PYbkwJ/MI=";

	public static final int PRIMARY = 0;
	public static final int BACKUP = 1;

	public static final int NONE = -1;

	private static final String[] KEYS = {PRIMARY_KEY_BASE64, BACKUP_KEY_BASE64};

	private static final String ALGORITHM = "Ed25519";

	private static final Set<Integer> REVOKED = new CopyOnWriteArraySet<>();

	private ContentSignature() {
	}

	public static boolean verify(byte[] data, byte[] signature) {
		return verifyBy(data, signature) != NONE;
	}

	public static int verifyBy(byte[] data, byte[] signature) {
		for (int index = 0; index < KEYS.length; index++) {
			if (REVOKED.contains(index)) {
				continue;
			}

			if (matches(KEYS[index], data, signature)) {
				return index;
			}
		}

		return NONE;
	}

	public static boolean revoke(Iterable<Integer> keys) {
		boolean changed = false;

		for (Integer key : keys) {
			if (key == null || key != PRIMARY) {
				continue;
			}

			changed |= REVOKED.add(key);
		}

		return changed;
	}

	public static Set<Integer> revoked() {
		return Set.copyOf(REVOKED);
	}

	private static boolean matches(String keyBase64, byte[] data, byte[] signature) {
		try {
			PublicKey key = KeyFactory.getInstance(ALGORITHM).generatePublic(
					new X509EncodedKeySpec(Base64.getDecoder().decode(keyBase64)));

			Signature verifier = Signature.getInstance(ALGORITHM);
			verifier.initVerify(key);
			verifier.update(data);
			return verifier.verify(signature);
		} catch (Exception e) {
			return false;
		}
	}
}
