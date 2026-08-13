package ml.mypals.microtimingreplay;

import ml.mypals.microtimingreplay.command.MTRCommand;
import ml.mypals.microtimingreplay.event.ItemTransferEvent;
import ml.mypals.microtimingreplay.event.MTREvents;
import ml.mypals.microtimingreplay.event.SelectionEventHandler;
import ml.mypals.microtimingreplay.network.MTRNetworking;
import ml.mypals.microtimingreplay.profile.ProfileManager;
import ml.mypals.microtimingreplay.profile.WorldScopedStorage;
import ml.mypals.microtimingreplay.replay.WorldBackupManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MicroTimingReplay implements ModInitializer {
	@SuppressWarnings("GrazieInspectionRunner")
    public static final String MOD_ID = "microtimingreplay";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static MinecraftServer server;

	@Override
	public void onInitialize() {
		
		ProfileManager.init();
		WorldBackupManager.init();
		MTREvents.init();

		// Payload types must be registered on both sides; this entrypoint runs on both.
		MTRNetworking.registerTypes();
		MTRNetworking.registerServerHandlers();

		CommandRegistrationCallback.EVENT.register(MTRCommand::register);
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {MicroTimingReplay.server = s;WorldScopedStorage.migrateCategoryLayout();});
		ServerLifecycleEvents.SERVER_STOPPING.register(MTRState::stoppingServer);
		ServerLifecycleEvents.SERVER_STOPPED.register(_ -> MicroTimingReplay.server = null);
		ServerTickEvents.END_SERVER_TICK.register(MTRState::checkAutoStop);
		SelectionEventHandler.register();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
