package ml.mypals.microtimingreplay.client.screen;

import ml.mypals.microtimingreplay.client.ClientReplayState;
import ml.mypals.microtimingreplay.client.MTRClientConfig;
import ml.mypals.microtimingreplay.client.MTRClientNetworking;
import ml.mypals.microtimingreplay.client.MTRKeys;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Client-side preferences — everything here is local to this player and never touches
 * the server. World-scoped settings live in the {@link FilterScreen} instead.
 */
@Environment(EnvType.CLIENT)
public class ClientConfigScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int LABEL_X = 30;
    private static final int CONTROL_WIDTH = 140;

    private Button unitButton;
    private Button amountButton;
    private Button invertButton;
    private Button followButton;
    private Button holdButton;
    private Button watchButton;

    /** Presets for the hold threshold; 0 means the panel appears the instant the key goes down. */
    private static final List<Integer> HOLD_DELAYS = List.of(0, 150, 250, 400, 600);

    public ClientConfigScreen() {
        super(MTRComponent.translatable("mtr.config.title", "MTR Client Settings"));
    }

    @Override
    protected void init() {
        int x = this.width - CONTROL_WIDTH - 30;
        int y = 44;

        unitButton = row(x, y, unitLabel(), b -> {
            MTRClientConfig.cycleStepUnit(1);
            b.setMessage(unitLabel());
        });
        y += ROW_HEIGHT;

        amountButton = row(x, y, amountLabel(), b -> {
            MTRClientConfig.cycleStepAmount(1);
            b.setMessage(amountLabel());
        });
        y += ROW_HEIGHT;

        invertButton = row(x, y, invertLabel(), b -> {
            MTRClientConfig.setInvertScroll(!MTRClientConfig.invertScroll());
            b.setMessage(invertLabel());
        });
        y += ROW_HEIGHT;

        followButton = row(x, y, followLabel(), b -> {
            MTRClientConfig.setFollowCursor(!MTRClientConfig.followCursor());
            b.setMessage(followLabel());
        });
        y += ROW_HEIGHT;

        holdButton = row(x, y, holdLabel(), b -> {
            int index = HOLD_DELAYS.indexOf(MTRClientConfig.panelHoldMillis());
            MTRClientConfig.setPanelHoldMillis(HOLD_DELAYS.get((index + 1) % HOLD_DELAYS.size()));
            b.setMessage(holdLabel());
        });
        y += ROW_HEIGHT;

        watchButton = row(x, y, watchLabel(), b -> {
            if (ClientReplayState.isWatching()) {
                MTRClientNetworking.unsubscribe();
            } else if (!ClientReplayState.runningSessions().isEmpty()) {
                MTRClientNetworking.subscribe(ClientReplayState.runningSessions().getFirst());
            }
            b.setMessage(watchLabel());
        });

        addRenderableWidget(Button.builder(MTRComponent.translatable("mtr.config.close", "Done"),
                b -> onClose()).bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    private Button row(int x, int y, Component label, Button.OnPress onPress) {
        Button button = Button.builder(label, onPress).bounds(x, y, CONTROL_WIDTH, 20).build();
        addRenderableWidget(button);
        return button;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── labels ───────────────────────────────────────────────────────────────

    private Component unitLabel() {
        String unit = MTRClientConfig.stepUnit();
        return MTRComponent.translatable("mtr.unit." + unit, unit);
    }

    private Component amountLabel() {
        return Component.literal(String.valueOf(MTRClientConfig.stepAmount()));
    }

    private Component invertLabel() {
        return MTRComponent.translatable(MTRClientConfig.invertScroll() ? "mtr.config.on" : "mtr.config.off",
                MTRClientConfig.invertScroll() ? "On" : "Off");
    }

    private Component followLabel() {
        return MTRComponent.translatable(MTRClientConfig.followCursor() ? "mtr.config.on" : "mtr.config.off",
                MTRClientConfig.followCursor() ? "On" : "Off");
    }

    private Component holdLabel() {
        int millis = MTRClientConfig.panelHoldMillis();
        return millis == 0
                ? MTRComponent.translatable("mtr.config.hold_instant", "Instant")
                : MTRComponent.translatable("mtr.config.hold_ms", "%d ms", millis);
    }

    private Component watchLabel() {
        if (ClientReplayState.isWatching()) {
            return MTRComponent.translatable("mtr.config.unwatch", "Stop watching");
        }
        return ClientReplayState.runningSessions().isEmpty()
                ? MTRComponent.translatable("mtr.config.no_replays", "No replay running")
                : MTRComponent.translatable("mtr.config.watch", "Watch %s",
                        ClientReplayState.runningSessions().getFirst());
    }

    // ── drawing ──────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(this.font, this.title, LABEL_X, 14, MTRWidgets.TEXT);

        label(graphics, unitButton, MTRComponent.translatable("mtr.config.unit", "Scroll steps by"));
        label(graphics, amountButton, MTRComponent.translatable("mtr.config.amount", "Units per notch"));
        label(graphics, invertButton, MTRComponent.translatable("mtr.config.invert", "Invert scroll"));
        label(graphics, followButton, MTRComponent.translatable("mtr.config.follow", "Timeline follows cursor"));
        label(graphics, holdButton, MTRComponent.translatable("mtr.config.hold", "Hold before panel opens"));
        label(graphics, watchButton, MTRComponent.translatable("mtr.config.subscription", "Subscription"));

        graphics.drawString(this.font, MTRComponent.translatable("mtr.config.keybind",
                        "Hold %s for the hotbar panel — rebind it in Controls",
                        MTRKeys.panel.getTranslatedKeyMessage().getString()),
                LABEL_X, this.height - 52, MTRWidgets.TEXT_DIM);

        if (!ClientReplayState.serverHasMod()) {
            graphics.drawString(this.font, MTRComponent.translatable("mtr.config.vanilla_server",
                            "This server does not run MicroTimingReplay — commands still work"),
                    LABEL_X, this.height - 40, MTRWidgets.TEXT_OFF);
        }
    }

    private void label(GuiGraphics graphics, Button button, Component text) {
        graphics.drawString(this.font, text, LABEL_X, button.getY() + 6, MTRWidgets.TEXT);
    }
}
