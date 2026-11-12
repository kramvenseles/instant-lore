package com.rufana.instantlore.content;

public final class LocalSoulBalance {
	private static volatile long balance = -1L;

	private LocalSoulBalance() {
	}

	public static void set(long value) {
		balance = value;
	}

	public static void clear() {
		balance = -1L;
	}

	public static boolean known() {
		return balance >= 0L;
	}

	public static long get() {
		return balance;
	}
}
