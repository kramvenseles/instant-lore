package com.rufana.instantlore.client.resource;

import com.rufana.instantlore.InstantLore;
import com.rufana.instantlore.content.LoreBlockDefinition;
import com.rufana.instantlore.content.LoreBlocks;
import com.rufana.instantlore.content.LoreContent;
import com.rufana.instantlore.content.LoreItemDefinition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.texture.Sprite;

public final class LoreModelCheck {
	private LoreModelCheck() {
	}

	public record Result(int checked, int broken) {
		public int ok() {
			return checked - broken;
		}
	}

	public static Result report() {
		MinecraftClient client = MinecraftClient.getInstance();
		BakedModelManager models = client.getBakedModelManager();
		BlockStateModel missing = models.getMissingModel();

		int checked = 0;
		int broken = 0;

		for (int slot = 0; slot < LoreContent.POOL_SIZE; slot++) {
			if (LoreContent.block(slot) == LoreBlockDefinition.EMPTY) {
				continue;
			}

			checked++;
			BlockStateModel model = models.getBlockModels()
					.getModel(LoreBlocks.get(slot).currentState());

			if (model == missing) {
				broken++;
				InstantLore.LOGGER.error("Slot {} ('{}') has no model",
						slot, LoreContent.block(slot).name());
				continue;
			}

			Sprite sprite = models.getBlockModels()
					.getModelParticleSprite(LoreBlocks.get(slot).currentState());
			String custom = LoreModels.customModelPath(slot);

			InstantLore.LOGGER.info("Slot {} ('{}') rendered with texture {}, geometry: {}",
					slot, LoreContent.block(slot).name(), sprite.getContents().getId(),
					custom == null ? "cube (no own model in content)" : custom);
		}

		//
		//
		Object missingItem = models.getItemModel(
				net.minecraft.util.Identifier.of(InstantLore.MOD_ID, "missing_model_probe"));

		for (int slot = 0; slot < LoreContent.ITEM_POOL_SIZE; slot++) {
			if (LoreContent.item(slot) == LoreItemDefinition.EMPTY) {
				continue;
			}

			checked++;
			Object model = models.getItemModel(
					net.minecraft.util.Identifier.of(InstantLore.MOD_ID, "item_" + slot));

			if (model == missingItem) {
				broken++;
				InstantLore.LOGGER.error("Item slot {} ('{}') has no model",
						slot, LoreContent.item(slot).name());
				continue;
			}

			InstantLore.LOGGER.info("Item slot {} ('{}') rendered",
					slot, LoreContent.item(slot).name());
		}

		if (broken == 0) {
			InstantLore.LOGGER.info("Models built for all occupied slots: {}", checked);
		} else {
			InstantLore.LOGGER.error("Slots without models: {} of {}", broken, checked);
		}

		return new Result(checked, broken);
	}
}
