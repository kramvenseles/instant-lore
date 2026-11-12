package com.rufana.instantlore.client.render;

public final class LoreCoords {
	private static final long SPIN_MS = 350L;

	private static volatile boolean scrambled = false;

	private LoreCoords() {
	}

	public static void set(boolean on) {
		scrambled = on;
	}

	public static boolean isScrambled() {
		return scrambled;
	}

	public static String scrambleNumbers(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}

		StringBuilder out = new StringBuilder(text.length());
		long slot = System.currentTimeMillis() / SPIN_MS;
		int i = 0;

		while (i < text.length()) {
			char c = text.charAt(i);
			boolean negative = c == '-' && i + 1 < text.length()
					&& Character.isDigit(text.charAt(i + 1));

			if (!negative && !Character.isDigit(c)) {
				out.append(c);
				i++;
				continue;
			}

			int start = i;
			if (negative) {
				i++;
			}
			while (i < text.length() && Character.isDigit(text.charAt(i))) {
				i++;
			}

			int digits = i - start - (negative ? 1 : 0);
			out.append(fake(slot, start, digits, negative));
		}

		return out.toString();
	}

	private static String fake(long slot, int at, int digits, boolean negative) {
		java.util.Random rnd = new java.util.Random(slot * 1000003L + at * 31L + digits);
		StringBuilder sb = new StringBuilder(digits + (negative ? 1 : 0));
		if (negative) {
			sb.append('-');
		}
		for (int k = 0; k < digits; k++) {
			sb.append((char) ('0' + rnd.nextInt(10)));
		}
		return sb.toString();
	}
}
