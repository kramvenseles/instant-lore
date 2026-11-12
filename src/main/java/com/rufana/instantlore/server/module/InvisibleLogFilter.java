package com.rufana.instantlore.server.module;

import com.rufana.instantlore.InstantLore;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

public final class InvisibleLogFilter extends AbstractFilter {

	private static boolean installed;

	private InvisibleLogFilter() {
		super(Result.NEUTRAL, Result.NEUTRAL);
	}

	public static void install() {
		if (installed) {
			return;
		}
		try {
			LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
			Configuration config = ctx.getConfiguration();
			config.addFilter(new InvisibleLogFilter());
			ctx.updateLoggers();
			installed = true;
		} catch (Throwable t) {
			InstantLore.LOGGER.warn(
					"Console filter for invisible players failed to install - names may show in the log", t);
		}
	}

	private static Result decide(String text) {
		return InvisibleModule.mentionsHidden(text) ? Result.DENY : Result.NEUTRAL;
	}

	@Override
	public Result filter(LogEvent event) {
		return guarded(() -> event.getMessage() == null ? Result.NEUTRAL
				: decide(event.getMessage().getFormattedMessage()));
	}

	@Override
	public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
		return guarded(() -> msg == null ? Result.NEUTRAL : decide(msg.getFormattedMessage()));
	}

	@Override
	public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
		return guarded(() -> msg == null ? Result.NEUTRAL : decide(String.valueOf(msg)));
	}

	@Override
	public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
		return guarded(() -> {
			if (msg == null) {
				return Result.NEUTRAL;
			}
			if (params == null || params.length == 0) {
				return decide(msg);
			}
			StringBuilder sb = new StringBuilder(msg);
			for (Object p : params) {
				sb.append(' ').append(p);
			}
			return decide(sb.toString());
		});
	}

	private static Result guarded(java.util.function.Supplier<Result> body) {
		try {
			return body.get();
		} catch (Throwable ignored) {
			return Result.NEUTRAL;
		}
	}
}
