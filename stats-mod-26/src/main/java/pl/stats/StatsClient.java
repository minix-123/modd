package pl.stats;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class StatsClient implements ClientModInitializer {
	public static KeyMapping openKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath("stats", "main"));
		openKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.stats.open", InputConstants.Type.KEYSYM, InputConstants.KEY_B, category));

		ClientTickEvents.END_CLIENT_TICK.register(StatsTracker::tick);
		ClientReceiveMessageEvents.GAME.register(StatsTracker::onGameMessage);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> StatsTracker.save());
	}
}
