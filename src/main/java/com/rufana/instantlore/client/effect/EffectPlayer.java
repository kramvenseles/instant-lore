package com.rufana.instantlore.client.effect;

import com.mojang.brigadier.StringReader;
import com.rufana.instantlore.InstantLore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.argument.ParticleEffectArgumentType;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public final class EffectPlayer {
	private static final List<Active> ACTIVE = new ArrayList<>();
	private static final Random RANDOM = new Random();

	private static final int MAX_ACTIVE = 32;

	private static final Map<String, Optional<ParticleEffect>> PARSED = new HashMap<>();
	private static final int MAX_PARSED = 256;

	private static final java.util.Set<String> COMPLAINED = new java.util.HashSet<>();

	private EffectPlayer() {
	}

	private static final class Active {
		final Effect effect;
		final Vec3d position;
		double elapsed;
		int spawned;

		Active(Effect effect, Vec3d position) {
			this.effect = effect;
			this.position = position;
		}
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(EffectPlayer::tick);
	}

	public static void play(Effect effect, Vec3d position) {
		MinecraftClient client = MinecraftClient.getInstance();

		if (client.world == null) {
			return;
		}

		synchronized (ACTIVE) {
			if (ACTIVE.size() >= MAX_ACTIVE) {
				InstantLore.LOGGER.warn("Too many effects at once ({}), new one skipped", ACTIVE.size());
				return;
			}

			ACTIVE.add(new Active(effect, position));
		}

		if (effect.sound() != null) {
			playSound(client.world, effect.sound(), position);
		}
	}

	private static void playSound(ClientWorld world, Effect.Sound sound, Vec3d position) {
		Identifier id = Identifier.tryParse(sound.id());
		SoundEvent event = id == null ? null : Registries.SOUND_EVENT.get(id);

		if (event == null) {
			InstantLore.LOGGER.warn("Unknown sound '{}'", sound.id());
			return;
		}

		world.playSoundClient(position.x, position.y, position.z, event,
				SoundCategory.AMBIENT, sound.volume(), sound.pitch(), false);
	}

	private static void tick(MinecraftClient client) {
		if (client.world == null) {
			synchronized (ACTIVE) {
				ACTIVE.clear();
			}
			PARSED.clear();
			return;
		}

		List<Active> snapshot;
		synchronized (ACTIVE) {
			snapshot = new ArrayList<>(ACTIVE);
		}

		List<Active> finished = new ArrayList<>();

		for (Active active : snapshot) {
			active.elapsed += 1.0 / 20.0;

			for (Effect.Emitter emitter : active.effect.emitters()) {
				try {
					emit(client.world, active, emitter);
				} catch (Throwable e) {
					InstantLore.LOGGER.error("Effect emitter failed, effect removed", e);
					finished.add(active);
					break;
				}
			}

			if (active.elapsed >= active.effect.duration()) {
				finished.add(active);
				InstantLore.LOGGER.info("Effect played: particles {}", active.spawned);
			}
		}

		if (!finished.isEmpty()) {
			synchronized (ACTIVE) {
				ACTIVE.removeAll(finished);
			}
		}
	}

	private static void emit(ClientWorld world, Active active, Effect.Emitter emitter) {
		ParticleEffect particle = particle(world, emitter.particle());

		if (particle == null) {
			return;
		}

		double perTick = emitter.rate() / 20.0;
		int count = (int) perTick;

		if (RANDOM.nextDouble() < perTick - count) {
			count++;
		}

		for (int i = 0; i < count; i++) {
			Vec3d point = shapePoint(emitter, active.elapsed);

			world.addParticleClient(particle,
					active.position.x + emitter.offset()[0] + point.x,
					active.position.y + emitter.offset()[1] + point.y,
					active.position.z + emitter.offset()[2] + point.z,
					emitter.velocity()[0] + jitter(emitter.spread()),
					emitter.velocity()[1] + jitter(emitter.spread()),
					emitter.velocity()[2] + jitter(emitter.spread()));

			active.spawned++;
		}
	}

	private static Vec3d shapePoint(Effect.Emitter emitter, double elapsed) {
		double radius = emitter.radius();

		return switch (emitter.shape()) {
			case "sphere" -> {
				double r = radius * Math.cbrt(RANDOM.nextDouble());
				yield onSphere(r);
			}
			case "shell" -> onSphere(radius);
			case "ring" -> {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				yield new Vec3d(radius * Math.cos(angle), 0, radius * Math.sin(angle));
			}
			case "disc" -> {
				double r = radius * Math.sqrt(RANDOM.nextDouble());
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				yield new Vec3d(r * Math.cos(angle), 0, r * Math.sin(angle));
			}
			case "box" -> new Vec3d(
					symmetric(emitter.size()[0]),
					symmetric(emitter.size()[1]),
					symmetric(emitter.size()[2]));
			case "line" -> {
				double t = RANDOM.nextDouble();
				yield new Vec3d(emitter.to()[0] * t, emitter.to()[1] * t, emitter.to()[2] * t);
			}
			case "cone" -> {
				double t = RANDOM.nextDouble();
				double r = radius * t * Math.sqrt(RANDOM.nextDouble());
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				yield new Vec3d(r * Math.cos(angle), emitter.height() * t, r * Math.sin(angle));
			}
			case "helix" -> {
				double t = RANDOM.nextDouble();
				double angle = (t * emitter.turns() + elapsed) * Math.PI * 2;
				yield new Vec3d(radius * Math.cos(angle), emitter.height() * t,
						radius * Math.sin(angle));
			}
			case "point" -> Vec3d.ZERO;
			//
			default -> {
				if (COMPLAINED.add(emitter.shape())) {
					InstantLore.LOGGER.warn("Shape '{}' unknown, particles go to a point",
							emitter.shape());
				}
				yield Vec3d.ZERO;
			}
		};
	}

	private static Vec3d onSphere(double r) {
		double theta = RANDOM.nextDouble() * Math.PI * 2;
		double phi = Math.acos(2 * RANDOM.nextDouble() - 1);

		return new Vec3d(
				r * Math.sin(phi) * Math.cos(theta),
				r * Math.cos(phi),
				r * Math.sin(phi) * Math.sin(theta));
	}

	private static double symmetric(double half) {
		return (RANDOM.nextDouble() - 0.5) * 2 * half;
	}

	private static double jitter(double spread) {
		return spread <= 0 ? 0 : (RANDOM.nextDouble() - 0.5) * 2 * spread;
	}

	private static ParticleEffect particle(ClientWorld world, String raw) {
		Optional<ParticleEffect> cached = PARSED.get(raw);

		if (cached != null) {
			return cached.orElse(null);
		}

		ParticleEffect parsed = null;

		try {
			parsed = ParticleEffectArgumentType.readParameters(
					new StringReader(raw), world.getRegistryManager());
		} catch (Throwable e) {
			InstantLore.LOGGER.warn("Particle '{}' not parsed: {}", raw, e.getMessage());
		}

		if (PARSED.size() < MAX_PARSED) {
			PARSED.put(raw, Optional.ofNullable(parsed));
		}

		return parsed;
	}
}
