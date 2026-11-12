package com.rufana.instantlore.content;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class LoreMob extends PathAwareEntity implements LoreShaped {
	private static final TrackedData<Integer> SLOT =
			DataTracker.registerData(LoreMob.class, TrackedDataHandlerRegistry.INTEGER);

	private static final TrackedData<Integer> APPEARANCE =
			DataTracker.registerData(LoreMob.class, TrackedDataHandlerRegistry.INTEGER);

	private static final TrackedData<Vector3fc> TILT =
			DataTracker.registerData(LoreMob.class, TrackedDataHandlerRegistry.VECTOR_3F);

	public LoreMob(EntityType<? extends LoreMob> type, World world) {
		super(type, world);
		setPersistent();
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return PathAwareEntity.createMobAttributes()
				.add(EntityAttributes.MAX_HEALTH, 20.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.25)
				.add(EntityAttributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(SLOT, -1);
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
		applyDefinition();
	}

	private void applyDefinition() {
		LoreEntityDefinition definition = loreDefinition();

		EntityAttributeInstance health = getAttributeInstance(EntityAttributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(Math.max(1.0, definition.health()));
			setHealth((float) health.getBaseValue());
		}

		EntityAttributeInstance speed = getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.setBaseValue(Math.max(0.0, definition.speed()));
		}

		setInvulnerable(definition.invulnerable());
		setNoGravity(!definition.gravity());
		setCustomName(definition.title() == null || definition.title().isBlank()
				? null : Text.literal(definition.title()));
		setCustomNameVisible(getCustomName() != null);

		calculateDimensions();

		goalSelector.clear(goal -> true);

		goalSelector.add(0, new SwimGoal(this));

		if (definition.wander()) {
			goalSelector.add(5, new WanderAroundFarGoal(this, 1.0));
		}

		if (definition.look()) {
			EntityAttributeInstance range = getAttributeInstance(EntityAttributes.FOLLOW_RANGE);
			float look = range == null ? 16.0F : (float) range.getValue();

			//
			goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, look, 1.0F));

		}
	}

	@Override
	public int getMaxHeadRotation() {
		return 180;
	}

	@Override
	protected EntityDimensions getBaseDimensions(EntityPose pose) {
		LoreEntityDefinition definition = loreDefinition();
		return EntityDimensions.changing(
				(float) Math.clamp(definition.width(), 0.05, 24.0),
				(float) Math.clamp(definition.height(), 0.05, 24.0));
	}

	@Override
	public boolean isInvulnerableTo(net.minecraft.server.world.ServerWorld world, DamageSource source) {
		return loreDefinition().invulnerable() || super.isInvulnerableTo(world, source);
	}

	@Override
	public void onTrackedDataSet(net.minecraft.entity.data.TrackedData<?> data) {
		super.onTrackedDataSet(data);

		if (SLOT.equals(data)) {
			calculateDimensions();
		}
	}

	@Override
	public boolean canHit() {
		return loreDefinition().clickable();
	}

	@Override
	protected void writeCustomData(WriteView view) {
		super.writeCustomData(view);
		view.putInt("lore_slot", loreSlot());
		view.putInt("lore_appearance", getDataTracker().get(APPEARANCE));
		Vector3fc tilt = getDataTracker().get(TILT);
		view.putFloat("lore_tilt_pitch", tilt.x());
		view.putFloat("lore_tilt_roll", tilt.z());
	}

	@Override
	protected void readCustomData(ReadView view) {
		super.readCustomData(view);
		int slot = view.getInt("lore_slot", -1);

		if (slot >= 0) {
			getDataTracker().set(SLOT, slot);
			applyDefinition();
		}

		getDataTracker().set(APPEARANCE, view.getInt("lore_appearance", -1));
		getDataTracker().set(TILT, new Vector3f(
				view.getFloat("lore_tilt_pitch", 0.0F), 0.0F, view.getFloat("lore_tilt_roll", 0.0F)));
	}
}
