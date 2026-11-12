package com.rufana.instantlore.server.task;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class TaskParser {
	private TaskParser() {
	}

	public static List<TaskStep> parse(String source) {
		List<TaskStep> steps = new ArrayList<>();

		for (String part : source.split(";")) {
			String trimmed = part.trim();

			if (!trimmed.isEmpty()) {
				steps.add(parseStep(trimmed));
			}
		}

		return steps;
	}

	private static TaskStep parseStep(String text) {
		String[] words = text.split("\\s+");
		String verb = words[0].toLowerCase();

		return switch (verb) {
			case "goto", "walk" -> TaskSteps.goTo(pos(words, 1),
					words.length > 4 ? words[4] : "walk", 120);
			case "break", "mine" -> TaskSteps.breakBlock(pos(words, 1), 180);
			case "look" -> TaskSteps.lookAt(
					number(words, 1) + 0.5, number(words, 2) + 0.5, number(words, 3) + 0.5);
			case "click" -> TaskSteps.click(
					words.length > 1 ? words[1] : "left",
					words.length > 2 ? (int) number(words, 2) : 1);
			case "wait" -> TaskSteps.wait(number(words, 1));
			case "say" -> TaskSteps.say(text.substring(3).trim());
			default -> throw new TaskFailure("неизвестный шаг: " + verb);
		};
	}

	private static BlockPos pos(String[] words, int from) {
		return BlockPos.ofFloored(
				number(words, from), number(words, from + 1), number(words, from + 2));
	}

	private static double number(String[] words, int index) {
		if (index >= words.length) {
			throw new TaskFailure("не хватает числа в шаге");
		}

		try {
			return Double.parseDouble(words[index]);
		} catch (NumberFormatException e) {
			throw new TaskFailure("'" + words[index] + "' — не число");
		}
	}
}
