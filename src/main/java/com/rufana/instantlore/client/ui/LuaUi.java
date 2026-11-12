package com.rufana.instantlore.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.Map;

public final class LuaUi {
	private static final int MAX_DEPTH = 32;

	private LuaUi() {
	}

	public static UiScreen toScreen(LuaTable spec, Map<String, LuaFunction> callbacks) {
		JsonElement root = convert(spec, callbacks, 0);
		return UiScreen.parse(root.isJsonObject() ? root.getAsJsonObject() : new JsonObject());
	}

	private static JsonElement convert(LuaValue value, Map<String, LuaFunction> callbacks, int depth) {
		if (depth > MAX_DEPTH) {
			throw new LuaError("lore.screen: слишком глубокая вложенность (> " + MAX_DEPTH + ")");
		}

		if (value.istable()) {
			LuaTable table = value.checktable();
			return isArray(table)
					? convertArray(table, callbacks, depth)
					: convertObject(table, callbacks, depth);
		}
		if (value.isboolean()) {
			return new JsonPrimitive(value.toboolean());
		}
		if (value.type() == LuaValue.TNUMBER) {
			return new JsonPrimitive(value.todouble());
		}
		if (value.type() == LuaValue.TSTRING) {
			return new JsonPrimitive(value.tojstring());
		}
		return JsonNull.INSTANCE;
	}

	private static JsonArray convertArray(LuaTable table, Map<String, LuaFunction> callbacks, int depth) {
		JsonArray array = new JsonArray();
		int length = table.length();
		for (int i = 1; i <= length; i++) {
			array.add(convert(table.get(i), callbacks, depth + 1));
		}
		return array;
	}

	private static JsonObject convertObject(LuaTable table, Map<String, LuaFunction> callbacks, int depth) {
		JsonObject object = new JsonObject();
		LuaValue key = LuaValue.NIL;

		while (true) {
			Varargs pair = table.next(key);
			key = pair.arg1();
			if (key.isnil()) {
				break;
			}
			LuaValue val = pair.arg(2);

			if (!key.isstring()) {
				continue;
			}
			String name = key.tojstring();

			if (val.isfunction()) {
				if (name.equals("on_click")) {
					String id = "cb" + (callbacks.size() + 1);
					callbacks.put(id, val.checkfunction());
					object.addProperty("action", id);
				}
			} else if (!val.isnil()) {
				object.add(name, convert(val, callbacks, depth + 1));
			}
		}

		return object;
	}

	private static boolean isArray(LuaTable table) {
		return !table.get(1).isnil();
	}
}
