package com.rufana.instantlore.client.render;

import net.minecraft.client.render.VertexConsumer;

public final class TintVertexConsumer implements VertexConsumer {
	private final VertexConsumer delegate;
	private final float factor;

	public TintVertexConsumer(VertexConsumer delegate, float factor) {
		this.delegate = delegate;
		this.factor = factor;
	}

	private int scale(int channel) {
		int v = (int) (channel * factor);
		return v < 0 ? 0 : (v > 255 ? 255 : v);
	}

	@Override
	public VertexConsumer color(int red, int green, int blue, int alpha) {
		delegate.color(scale(red), scale(green), scale(blue), alpha);
		return this;
	}

	@Override
	public VertexConsumer color(int argb) {
		int a = (argb >>> 24) & 0xFF;
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		delegate.color((a << 24) | (scale(r) << 16) | (scale(g) << 8) | scale(b));
		return this;
	}

	@Override
	public VertexConsumer vertex(float x, float y, float z) {
		delegate.vertex(x, y, z);
		return this;
	}

	@Override
	public VertexConsumer texture(float u, float v) {
		delegate.texture(u, v);
		return this;
	}

	@Override
	public VertexConsumer overlay(int u, int v) {
		delegate.overlay(u, v);
		return this;
	}

	@Override
	public VertexConsumer light(int u, int v) {
		delegate.light(u, v);
		return this;
	}

	@Override
	public VertexConsumer normal(float x, float y, float z) {
		delegate.normal(x, y, z);
		return this;
	}

	@Override
	public VertexConsumer lineWidth(float width) {
		delegate.lineWidth(width);
		return this;
	}
}
