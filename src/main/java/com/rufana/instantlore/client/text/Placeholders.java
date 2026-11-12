package com.rufana.instantlore.client.text;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Placeholders {
	private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]*)\\}");

	private Placeholders() {
	}

	public static String substitute(String template, Map<String, String> values) {
		if (template == null || !template.contains("${")) {
			return template == null ? "" : template;
		}

		Matcher matcher = PLACEHOLDER.matcher(template);
		StringBuilder result = new StringBuilder();

		while (matcher.find()) {
			String value = values.get(matcher.group(1));

			matcher.appendReplacement(result, Matcher.quoteReplacement(
					value != null ? value : matcher.group()));
		}

		matcher.appendTail(result);
		return result.toString();
	}

	public static Map<String, String> flatten(JsonObject object) {
		Map<String, String> values = new HashMap<>();

		if (object == null) {
			return values;
		}

		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			if (entry.getValue().isJsonPrimitive()) {
				values.put(entry.getKey(), entry.getValue().getAsString());
			}
		}

		return values;
	}
}
