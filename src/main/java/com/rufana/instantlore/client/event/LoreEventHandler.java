package com.rufana.instantlore.client.event;

import com.rufana.instantlore.event.LoreEvent;

@FunctionalInterface
public interface LoreEventHandler {
	void handle(LoreEvent event);
}
