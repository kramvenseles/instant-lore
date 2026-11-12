package com.rufana.instantlore.content;

public final class ServerContentState {
	private static volatile String version = "0";

	private ServerContentState() {
	}

	public static String version() {
		return version;
	}

	public static void setVersion(String newVersion) {
		version = newVersion;
	}
}
