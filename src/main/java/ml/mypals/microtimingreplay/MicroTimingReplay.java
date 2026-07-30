package ml.mypals.microtimingreplay;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import ml.mypals.microtimingreplay.command.MTRCommand;
import ml.mypals.microtimingreplay.profile.ProfileManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MicroTimingReplay implements ModInitializer {
	public static final String MOD_ID = "microtimingreplay";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static MinecraftServer server;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Initializing MicroTimingReplay (MTR)...");
		
		ProfileManager.init();

		CommandRegistrationCallback.EVENT.register(MTRCommand::register);

		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			MicroTimingReplay.server = s;
		});

		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(MTRState::checkAutoStop);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
