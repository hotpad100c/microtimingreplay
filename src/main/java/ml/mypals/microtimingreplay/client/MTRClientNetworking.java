package ml.mypals.microtimingreplay.client;

import ml.mypals.microtimingreplay.config.RecordMode;
import ml.mypals.microtimingreplay.client.screen.FilterScreen;
import ml.mypals.microtimingreplay.client.screen.TimelineScreen;
import ml.mypals.microtimingreplay.network.MTRPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.Screen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;


@Environment(EnvType.CLIENT)
public class MTRClientNetworking {

    public static void registerClientHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(MTRPayloads.HelloS2C.TYPE, (payload, context) ->
                ClientReplayState.onHello(payload.protocol() == MTRPayloads.PROTOCOL_VERSION, payload.allowed()));

        ClientPlayNetworking.registerGlobalReceiver(MTRPayloads.SessionsS2C.TYPE, (payload, context) ->
                ClientReplayState.onSessions(payload.running(), payload.subscribed(), payload.cameraFollow()));

        ClientPlayNetworking.registerGlobalReceiver(MTRPayloads.TimelineS2C.TYPE, (payload, context) ->
                ClientReplayState.onTimeline(payload));

        ClientPlayNetworking.registerGlobalReceiver(MTRPayloads.CursorS2C.TYPE, (payload, context) ->
                ClientReplayState.onCursor(payload));

        ClientPlayNetworking.registerGlobalReceiver(MTRPayloads.DetailsS2C.TYPE, (payload, context) ->
                ClientReplayState.onDetails(payload));

        ClientPlayNetworking.registerGlobalReceiver(MTRPayloads.FilterS2C.TYPE, (payload, context) ->
                ClientReplayState.onFilter(payload.rows()));

        ClientPlayNetworking.registerGlobalReceiver(MTRPayloads.OpenScreenS2C.TYPE, (payload, context) -> {
            Screen screen = switch (payload.screen()) {
                case MTRPayloads.OpenScreenS2C.TIMELINE -> new TimelineScreen();
                case MTRPayloads.OpenScreenS2C.FILTER -> new FilterScreen();
                default -> null;
            };
            if (screen != null) {
                context.client().setScreen(screen);
            }
        });
    }

    /**
     * Guarded so a vanilla server — which never registered our channels — does not get
     * an exception thrown at it every time the player scrolls.
     */
    private static void send(CustomPacketPayload payload) {
        if (ClientPlayNetworking.canSend(payload.type())) {
            ClientPlayNetworking.send(payload);
        }
    }

    public static void sendHello() {
        send(new MTRPayloads.HelloC2S(MTRPayloads.PROTOCOL_VERSION));
    }

    public static void requestTimeline() {
        send(MTRPayloads.RequestTimelineC2S.INSTANCE);
    }

    public static void requestFilter() {
        send(MTRPayloads.RequestFilterC2S.INSTANCE);
    }

    public static void requestDetails(int step) {
        send(new MTRPayloads.RequestDetailsC2S(step));
    }

    public static void step(boolean forward) {
        step(MTRClientConfig.stepUnit(), MTRClientConfig.stepAmount(), forward);
    }

    public static void step(String unit, int amount, boolean forward) {
        send(new MTRPayloads.StepC2S(unit, amount, forward));
    }

    public static void jump(int step) {
        send(new MTRPayloads.JumpC2S(step));
    }

    public static void subscribe(String profile) {
        send(new MTRPayloads.SubscribeC2S(profile));
    }

    public static void unsubscribe() {
        send(new MTRPayloads.SubscribeC2S(""));
    }

    public static void setFilter(String eventId, RecordMode mode) {
        send(new MTRPayloads.SetFilterC2S(eventId, mode));
    }

    public static void resetFilter() {
        send(MTRPayloads.ResetFilterC2S.INSTANCE);
    }

    public static void setCameraFollow(boolean enabled) {
        send(new MTRPayloads.SetCameraFollowC2S(enabled));
        ClientReplayState.setCameraFollow(enabled);
    }
}
