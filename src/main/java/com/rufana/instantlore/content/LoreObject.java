package com.rufana.instantlore.content;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class LoreObject extends Entity implements LoreShaped {
	private static final TrackedData<Integer> SLOT =
			DataTracker.registerData(LoreObject.class, TrackedDataHandlerRegistry.INTEGER);

	private static final TrackedData<Vector3fc> SEAT =
			DataTracker.registerData(LoreObject.class, TrackedDataHandlerRegistry.VECTOR_3F);

	private static final Vector3fc NO_SEAT = new Vector3f(Float.NaN, Float.NaN, Float.NaN);

	private static final TrackedData<Integer> APPEARANCE =
			DataTracker.registerData(LoreObject.class, TrackedDataHandlerRegistry.INTEGER);

	private static final TrackedData<Vector3fc> TILT =
			DataTracker.registerData(LoreObject.class, TrackedDataHandlerRegistry.VECTOR_3F);

	private float turnedAt = Float.NaN;

	public LoreObject(EntityType<? extends LoreObject> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(SLOT, -1);
		builder.add(SEAT, NO_SEAT);
		builder.add(APPEARANCE, -1);
		builder.add(TILT, new Vector3f());
	}

	public void setTilt(double pitch, double roll) {
		getDataTracker().set(TILT, new Vector3f((float) pitch, 0.0F, (float) roll));
	}

	@Override
	public float renderPitch() {
		return getDataTracker().get(TILT).x();
	}

	@Override
	public float renderRoll() {
		return getDataTracker().get(TILT).z();
	}

	public void setAppearance(int blockSlot) {
		getDataTracker().set(APPEARANCE, blockSlot);
	}

	public void clearAppearance() {
		getDataTracker().set(APPEARANCE, -1);
	}

	@Override
	public int appearanceBlockSlot() {
		int override = getDataTracker().get(APPEARANCE);
		return (override >= 0 && override < LoreContent.POOL_SIZE)
				? override : loreDefinition().blockSlot();
	}

	@Override
	public int loreSlot() {
		return getDataTracker().get(SLOT);
	}

	public void setLoreSlot(int slot) {
		getDataTracker().set(SLOT, slot);
		LoreEntityDefinition definition = loreDefinition();
		setNoGravity(!definition.gravity());
		setInvulnerable(definition.invulnerable());
		calculateDimensions();
	}

	@Override
	public void onTrackedDataSet(net.minecraft.entity.data.TrackedData<?> data) {
		super.onTrackedDataSet(data);

		if (SLOT.equals(data)) {
			calculateDimensions();
		}
	}

	public void setSeat(double x, double y, double z) {
		getDataTracker().set(SEAT, new Vector3f((float) x, (float) y, (float) z));
	}

	public void clearSeat() {
		getDataTracker().set(SEAT, NO_SEAT);
	}

	@Override
	protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions,
			float scaleFactor) {
		Vector3fc seat = getDataTracker().get(SEAT);

		if (Float.isNaN(seat.x())) {
			return super.getPassengerAttachmentPos(passenger, dimensions, scaleFactor);
		}

		return new Vec3d(seat.x() * scaleFactor, seat.y() * scaleFactor, seat.z() * scaleFactor)
				.rotateY(-getYaw() * ((float) Math.PI / 180.0F));
	}

	@Override
	public boolean canHit() {
		return loreDefinition().clickable();
	}

	@Override
	public boolean isCollidable(Entity other) {
		return loreDefinition().collidable() && !hasPassenger(other);
	}

	@Override
	protected void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
		super.updatePassengerPosition(passenger, updater);

		float yaw = getYaw();

		if (Float.isNaN(turnedAt)) {
			turnedAt = yaw;
			return;
		}

		float delta = net.minecraft.util.math.MathHelper.wrapDegrees(yaw - turnedAt);

		if (delta != 0.0F) {
			turnedAt = yaw;
			passenger.setYaw(passenger.getYaw() + delta);
			passenger.setHeadYaw(passenger.getYaw());

			if (passenger.getEntityWorld().isClient()) {
				RideTurn.record(passenger.getId(), delta);
			}
		}
	}

	@Override
	public boolean damage(net.minecraft.server.world.ServerWorld world,
			net.minecraft.entity.damage.DamageSource source, float amount) {
		return false;
	}

	@Override
	public EntityDimensions getDimensions(EntityPose pose) {
		LoreEntityDefinition definition = loreDefinition();
		return EntityDimensions.changing(
				(float) Math.clamp(definition.width(), 0.05, 24.0),
				(float) Math.clamp(definition.height(), 0.05, 24.0));
	}

	@Override
	protected void writeCustomData(WriteView view) {
		view.putInt("lore_slot", loreSlot());
		view.putInt("lore_appearance", getDataTracker().get(APPEARANCE));
		Vector3fc tilt = getDataTracker().get(TILT);
		view.putFloat("lore_tilt_pitch", tilt.x());
		view.putFloat("lore_tilt_roll", tilt.z());
	}

	@Override
	protected void readCustomData(ReadView view) {
		int slot = view.getInt("lore_slot", -1);

		if (slot >= 0) {
			setLoreSlot(slot);
		}

		getDataTracker().set(APPEARANCE, view.getInt("lore_appearance", -1));
		getDataTracker().set(TILT, new Vector3f(
				view.getFloat("lore_tilt_pitch", 0.0F), 0.0F, view.getFloat("lore_tilt_roll", 0.0F)));
	}
}
