package com.rufana.instantlore.net;

import com.rufana.instantlore.InstantLore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

public final class ContentSource {
	private static final Duration TIMEOUT = Duration.ofSeconds(20);

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(6);

	private static final long MAX_ASSET_BYTES = 32L * 1024 * 1024;

	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	private ContentSource() {
	}

	public record SignedManifest(String json, byte[] signature) {
	}

	public static SignedManifest fetchManifest() throws IOException {
		return fromAnySource("манифест", base -> {
			byte[] manifest = getFrom(base, "manifest.json");
			byte[] signature = getFrom(base, "manifest.sig");

			if (!ContentSignature.verify(manifest, signature)) {
				throw new IOException("подпись не сошлась");
			}

			return new SignedManifest(
					new String(manifest, java.nio.charset.StandardCharsets.UTF_8), signature);
		});
	}

	public static String fetchModules() {
		try {
			return fromAnySource("конфиг модулей", base -> {
				byte[] doc = getFrom(base, "modules.json");
				byte[] sig = getFrom(base, "modules.sig");

				if (!ContentSignature.verify(doc, sig)) {
					throw new IOException("подпись не сошлась");
				}

				return new String(doc, java.nio.charset.StandardCharsets.UTF_8);
			});
		} catch (IOException e) {
			InstantLore.LOGGER.debug("modules.json missing from the distribution or failed verification - using defaults");
			return null;
		}
	}

	public static String fetchClientVersion() {
		lastClientVersionError = "";
		try {
			return fromAnySource("версия клиента", base -> {
				byte[] doc = getFrom(base, "client-version.json");
				byte[] sig = getFrom(base, "client-version.sig");

				if (!ContentSignature.verify(doc, sig)) {
					throw new IOException("подпись не сошлась");
				}

				return new String(doc, java.nio.charset.StandardCharsets.UTF_8);
			});
		} catch (IOException e) {
			lastClientVersionError = String.valueOf(e.getMessage());
			InstantLore.LOGGER.debug("client-version.json not present on the distribution - jar auto-update skipped");
			return null;
		}
	}

	public static String lastClientVersionError() {
		return lastClientVersionError;
	}

	private static volatile String lastClientVersionError = "";

	public static String fetchCompanions() {
		try {
			return fromAnySource("список companion-модов", base -> {
				byte[] doc = getFrom(base, "companions.json");
				byte[] sig = getFrom(base, "companions.sig");

				if (!ContentSignature.verify(doc, sig)) {
					throw new IOException("подпись не сошлась");
				}

				return new String(doc, java.nio.charset.StandardCharsets.UTF_8);
			});
		} catch (IOException e) {
			InstantLore.LOGGER.debug("companions.json not present on the distribution - companion mod delivery skipped");
			return null;
		}
	}

	public static byte[] fetchAsset(String path, String expectedSha256, long expectedSize)
			throws IOException {
		if (expectedSize > MAX_ASSET_BYTES) {
			throw new IOException("Ассет " + path + " заявлен размером " + expectedSize
					+ " байт при пределе " + MAX_ASSET_BYTES);
		}

		return fromAnySource(path, base -> {
			byte[] data = getFrom(base, path);

			if (data.length != expectedSize) {
				throw new IOException("размер не сошёлся: заявлено "
						+ expectedSize + ", получено " + data.length);
			}

			String actual = sha256(data);

			if (!actual.equalsIgnoreCase(expectedSha256)) {
				throw new IOException("хэш не сошёлся: ожидался "
						+ expectedSha256 + ", получен " + actual);
			}

			return data;
		});
	}

	private static void checkPath(String path) throws IOException {
		if (path.isEmpty() || path.startsWith("/") || path.contains("..")
				|| path.contains("//") || path.contains("\\")) {
			throw new IOException("Недопустимый путь ассета: " + path);
		}
	}

	private interface FromSource<T> {
		T apply(String base) throws Exception;
	}

	private static <T> T fromAnySource(String what, FromSource<T> action) throws IOException {
		StringBuilder failures = new StringBuilder();

		for (String base : ContentSources.ordered()) {
			try {
				T result = action.apply(base);
				ContentSources.succeeded(base);
				return result;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("загрузка прервана", e);
			} catch (Exception e) {
				ContentSources.failed(base);
				failures.append("\n  ").append(base).append(" — ").append(e.getMessage());
			}
		}

		throw new IOException("Ни один источник не дал годный " + what + ":" + failures);
	}

	private static byte[] getFrom(String base, String path)
			throws IOException, InterruptedException {
		checkPath(path);

		URI uri = URI.create(base + path);

		if (!uri.normalize().toString().startsWith(base)) {
			throw new IOException("путь увёл запрос за пределы " + base + ": " + uri);
		}

		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(TIMEOUT)
				.header("User-Agent", "instant-lore-mod")
				.GET()
				.build();

		HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

		if (response.statusCode() != 200) {
			throw new IOException("HTTP " + response.statusCode());
		}

		InstantLore.LOGGER.debug("Downloaded {} from {} ({} bytes)", path, base, response.body().length);
		return response.body();
	}

	public static void refreshSources() {
		ContentSources.startCycle();

		byte[][] pair;

		try {
			pair = fromAnySource("список источников", base -> {
				byte[] doc = getFrom(base, "sources.json");
				byte[] sig = getFrom(base, "sources.sig");

				if (ContentSignature.verifyBy(doc, sig) == ContentSignature.NONE) {
					throw new IOException("подпись не сошлась");
				}

				return new byte[][] {doc, sig};
			});
		} catch (Exception e) {
			InstantLore.LOGGER.debug("No source list on the distribution - keeping the current one");
			return;
		}

		ContentSources.accept(pair[0], pair[1], "с раздачи", true);
	}

	public static String sha256(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 обязан быть в любой JVM", e);
		}
	}
}
