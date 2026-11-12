package com.rufana.instantlore.client.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public final class LuaJson {
	private static final int MAX_DEPTH = 32;

	private LuaJson() {
	}

	public static LuaValue toLua(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return LuaValue.NIL;
		}

		if (element.isJsonPrimitive()) {
			JsonPrimitive primitive = element.getAsJsonPrimitive();

			if (primitive.isBoolean()) {
				return LuaValue.valueOf(primitive.getAsBoolean());
			}
			if (primitive.isNumber()) {
				return LuaValue.valueOf(primitive.getAsDouble());
			}
			return LuaValue.valueOf(primitive.getAsString());
		}

		if (element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			LuaValue table = LuaValue.tableOf();

			for (int i = 0; i < array.size(); i++) {
				table.set(i + 1, toLua(array.get(i)));
			}

			return table;
		}

		JsonObject object = element.getAsJsonObject();
		LuaValue table = LuaValue.tableOf();
		object.entrySet().forEach(entry -> table.set(entry.getKey(), toLua(entry.getValue())));
		return table;
	}

	public static JsonObject toJsonObject(LuaValue value) {
		JsonObject object = new JsonObject();

		if (!value.istable()) {
			return object;
		}

		LuaTable table = value.checktable();
		LuaValue key = LuaValue.NIL;

		while (true) {
			Varargs entry = table.next(key);
			key = entry.arg1();

			if (key.isnil()) {
				break;
			}

			object.add(key.tojstring(), toJson(entry.arg(2), 1));
		}

		return object;
	}

	public static JsonElement toJsonValue(LuaValue value) {
		return toJson(value, 1);
	}

	private static JsonElement toJson(LuaValue value, int depth) {
		if (depth > MAX_DEPTH) {
			throw new LuaError("таблица вложена глубже " + MAX_DEPTH + " — возможно, ссылается на себя");
		}

		if (value.isnil()) {
			return JsonNull.INSTANCE;
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
		if (value.istable()) {
			return tableToJson(value.checktable(), depth);
		}

		throw new LuaError("в данные события попал тип, который не сериализуется: " + value.typename());
	}

	private static JsonElement tableToJson(LuaTable table, int depth) {
		int length = table.length();
		int keys = 0;
		boolean array = length > 0;

		LuaValue key = LuaValue.NIL;

		while (true) {
			Varargs entry = table.next(key);
			key = entry.arg1();

			if (key.isnil()) {
				break;
			}

			keys++;

			if (!key.isint() || key.toint() < 1 || key.toint() > length) {
				array = false;
			}
		}

		if (array && keys == length) {
			JsonArray json = new JsonArray();

			for (int i = 1; i <= length; i++) {
				json.add(toJson(table.get(i), depth + 1));
			}

			return json;
		}

		JsonObject json = new JsonObject();
		key = LuaValue.NIL;

		while (true) {
			Varargs entry = table.next(key);
			key = entry.arg1();

			if (key.isnil()) {
				break;
			}

			json.add(key.tojstring(), toJson(entry.arg(2), depth + 1));
		}

		return json;
	}
}
