package com.rufana.instantlore.client;

public final class ClientContentState {
	public static final String NOTHING = "";

	private static volatile String version = NOTHING;

	private ClientContentState() {
	}

	public static String version() {
		return version;
	}

	public static void setVersion(String newVersion) {
		version = newVersion;
	}

	public static boolean isStale(String serverVersion) {
		return !version.equals(serverVersion);
	}
}
