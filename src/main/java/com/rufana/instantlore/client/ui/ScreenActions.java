package com.rufana.instantlore.client.ui;

import java.util.Map;

public interface ScreenActions {
	void onAction(String action, Map<String, String> inputs, int cx, int cy);

	default void onClosed() {
	}

	default void onScroll(int mx, int my, int dir) {
	}

	default void onDrag(int mx, int my, int dx, int dy) {
	}

	default void onRelease(int mx, int my) {
	}
}
