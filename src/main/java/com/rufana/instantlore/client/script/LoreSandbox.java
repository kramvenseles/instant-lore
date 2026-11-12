package com.rufana.instantlore.client.script;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseMathLib;

public final class LoreSandbox {
	private static final int INSTRUCTION_BUDGET = 2_000_000;

	private static final long ALLOCATION_BUDGET = 64L * 1024 * 1024;

	private static final int ALLOCATION_CHECK_INTERVAL = 16;

	private static final int MAX_STRING_LENGTH = 1024 * 1024;

	private LoreSandbox() {
	}

	public static Globals create() {
		Globals globals = new Globals();

		LuaTable packageStub = new LuaTable();
		packageStub.set("loaded", new LuaTable());
		globals.set("package", packageStub);

		globals.load(new JseBaseLib());
		globals.load(new TableLib());
		globals.load(new StringLib());
		globals.load(new JseMathLib());

		LuaC.install(globals);

		installLimits(globals);
		capStringRep(globals);
		protectStringMetatable(globals);
		removeDangerousGlobals(globals);

		return globals;
	}

	private static void installLimits(Globals globals) {
		globals.load(new DebugLib() {
			@Override
			public void onInstruction(int pc, Varargs v, int top) {
				LoreScripts.Budget budget = LoreScripts.INSTRUCTIONS.get();

				if (++budget.count > INSTRUCTION_BUDGET) {
					throw new LuaError("превышен лимит инструкций (" + INSTRUCTION_BUDGET + ")");
				}

				if (budget.count % ALLOCATION_CHECK_INTERVAL == 0) {
					checkAllocation(budget);
				}

				super.onInstruction(pc, v, top);
			}
		});
	}

	private static void checkAllocation(LoreScripts.Budget budget) {
		long allocated = LoreScripts.allocatedBytes();

		if (allocated < 0 || budget.allocatedAtStart < 0) {
			return;
		}

		long used = allocated - budget.allocatedAtStart;

		if (used > ALLOCATION_BUDGET) {
			throw new LuaError("превышен лимит памяти ("
					+ (ALLOCATION_BUDGET / 1024 / 1024) + " МБ на вызов, выделено "
					+ (used / 1024 / 1024) + " МБ)");
		}
	}

	private static void capStringRep(Globals globals) {
		LuaValue stringLibrary = globals.get("string");
		LuaValue original = stringLibrary.get("rep");

		stringLibrary.set("rep", new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs args) {
				long times = Math.max(args.optlong(2, 0), 0);
				long piece = args.checkjstring(1).length();
				long separator = args.narg() >= 3 ? args.checkjstring(3).length() : 0;

				long length = piece * times + separator * Math.max(times - 1, 0);

				if (length > MAX_STRING_LENGTH) {
					throw new LuaError("string.rep: результат " + length
							+ " байт при пределе " + MAX_STRING_LENGTH);
				}

				return original.invoke(args);
			}
		});
	}

	private static void protectStringMetatable(Globals globals) {
		LuaValue stringLibrary = globals.get("string");

		LuaTable metatable = new LuaTable();
		metatable.set(LuaValue.INDEX, new TwoArgFunction() {
			@Override
			public LuaValue call(LuaValue self, LuaValue key) {
				return stringLibrary.get(key);
			}
		});
		metatable.set(LuaValue.METATABLE, LuaValue.valueOf("заперто"));

		LuaString.s_metatable = metatable;
	}

	private static void removeDangerousGlobals(Globals globals) {
		for (String name : new String[]{
				"debug", "dofile", "loadfile", "load", "loadstring", "collectgarbage", "package"}) {
			globals.set(name, LuaValue.NIL);
		}

		globals.finder = filename -> null;
	}
}
