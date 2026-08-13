package ml.mypals.microtimingreplay.client.screen;

import ml.mypals.microtimingreplay.client.ClientReplayState;
import ml.mypals.microtimingreplay.client.MTRClientConfig;
import ml.mypals.microtimingreplay.client.MTRClientNetworking;
import ml.mypals.microtimingreplay.client.MTRKeys;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import static ml.mypals.microtimingreplay.client.screen.MTRWidgets.playButtonClickSound;

/**
 * The hold-to-show panel above the hotbar.
 *
 * <p>It is a {@link Screen} rather than a HUD layer on purpose: a screen gets the free
 * cursor, hover, and click handling that "point at a card and click it" needs, and it
 * costs nothing else here because {@link #isPauseScreen()} is false and the background
 * is left untouched. It closes itself the moment the key comes back up.
 */
@Environment(EnvType.CLIENT)
public class MTRPanelScreen extends Screen {

    private static final int PANEL_WIDTH = 430;
    private static final int PANEL_HEIGHT = 64;
    /** Clearance for the hotbar plus the experience bar underneath the panel. */
    private static final int BOTTOM_MARGIN = 46;

    private static final int CARD_HEIGHT = 18;
    private static final int CARD_GAP = 4;

    private int panelX;
    private int panelY;

    private record Card(Component label, int x, int y, int width, boolean active,
                        Runnable onLeft, Runnable onRight) {}

    public MTRPanelScreen() {
        super(MTRComponent.translatable("mtr.panel.title", "MicroTimingReplay"));
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = this.height - BOTTOM_MARGIN - PANEL_HEIGHT;

        // Cheap enough to refresh every time the panel comes up, and it means the
        // timeline screen never opens onto stale rows.
        if (ClientReplayState.isWatching()) {
            MTRClientNetworking.requestTimeline();
        }
        MTRClientNetworking.requestFilter();
    }

    @Override
    public void tick() {
        if (!MTRKeys.isPanelHeld()) {
            onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    /**
     * Rebuilt on demand rather than cached, so a session appearing or disappearing
     * mid-hold is reflected without any invalidation bookkeeping.
     */
    private List<Card> buildCards() {
        List<Card> cards = new ArrayList<>();

        if (ClientReplayState.isWatching()) {
            cards.add(new Card(MTRComponent.translatable("mtr.panel.timeline", "Timeline"), 0, 0, 62, false,
                    () -> this.minecraft.setScreen(new TimelineScreen()), null));
            cards.add(new Card(MTRComponent.translatable("mtr.panel.filter", "Filter"), 0, 0, 56, false,
                    () -> this.minecraft.setScreen(new FilterScreen()), null));
            cards.add(new Card(MTRComponent.translatable("mtr.panel.settings", "Settings"), 0, 0, 56, false,
                    () -> this.minecraft.setScreen(new ClientConfigScreen()), null));
            cards.add(new Card(
                    MTRComponent.translatable("mtr.panel.unit", "Unit: %s", unitLabel()), 0, 0, 88, false,
                    () -> MTRClientConfig.cycleStepUnit(1), () -> MTRClientConfig.cycleStepUnit(-1)));
            cards.add(new Card(
                    MTRComponent.translatable("mtr.panel.amount", "Step: %d", MTRClientConfig.stepAmount()), 0, 0, 62, false,
                    () -> MTRClientConfig.cycleStepAmount(1), () -> MTRClientConfig.cycleStepAmount(-1)));
            cards.add(new Card(MTRComponent.translatable("mtr.panel.help", "Guide"), 0, 0, 56, false,
                    () -> this.minecraft.setScreen(new HelpScreen(this)), null));
        } else {
            // Nothing to step through yet: offer the running replays to watch instead.
            for (String profile : ClientReplayState.runningSessions()) {
                cards.add(new Card(
                        MTRComponent.translatable("mtr.panel.watch", "Watch: %s", profile), 0, 0, 96, false,
                        () -> MTRClientNetworking.subscribe(profile), null));
            }
            cards.add(new Card(MTRComponent.translatable("mtr.panel.filter", "Filter"), 0, 0, 56, false,
                    () -> this.minecraft.setScreen(new FilterScreen()), null));
            cards.add(new Card(MTRComponent.translatable("mtr.panel.settings", "Settings"), 0, 0, 56, false,
                    () -> this.minecraft.setScreen(new ClientConfigScreen()), null));
            cards.add(new Card(MTRComponent.translatable("mtr.panel.help", "Guide"), 0, 0, 56, false,
                    () -> this.minecraft.setScreen(new HelpScreen(this)), null));
        }

        return position(cards);
    }

    /** Centres the row inside the panel and stamps each card's final x. */
    private List<Card> position(List<Card> cards) {
        if (cards.isEmpty()) return cards;

        int totalWidth = (cards.size() - 1) * CARD_GAP;
        for (Card card : cards) {
            totalWidth += card.width();
        }

        int x = panelX + (PANEL_WIDTH - totalWidth) / 2;
        int y = panelY + 24;

        List<Card> placed = new ArrayList<>(cards.size());
        for (Card card : cards) {
            placed.add(new Card(card.label(), x, y, card.width(), card.active(), card.onLeft(), card.onRight()));
            x += card.width() + CARD_GAP;
        }
        return placed;
    }

    private String unitLabel() {
        String unit = MTRClientConfig.stepUnit();
        return MTRComponent.translatable("mtr.unit." + unit, unit).getString();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        MTRWidgets.panel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT,
                MTRWidgets.PANEL_BG, MTRWidgets.PANEL_BORDER);

        graphics.centeredText(this.font, statusLine(), panelX + PANEL_WIDTH / 2, panelY + 7, MTRWidgets.TEXT);

        for (Card card : buildCards()) {
            boolean hovered = MTRWidgets.isOver(mouseX, mouseY, card.x(), card.y(), card.width(), CARD_HEIGHT);
            MTRWidgets.card(graphics, this.font, card.label(), card.x(), card.y(), card.width(), CARD_HEIGHT,
                    hovered, card.active());
        }

        graphics.centeredText(this.font, hintLine(), panelX + PANEL_WIDTH / 2,
                panelY + PANEL_HEIGHT - 13, MTRWidgets.TEXT_DIM);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private Component statusLine() {
        if (!ClientReplayState.allowed()) {
            return MTRComponent.translatable("mtr.panel.no_permission",
                    "You lack permission to drive replays on this server");
        }
        if (!ClientReplayState.isWatching()) {
            return ClientReplayState.runningSessions().isEmpty()
                    ? MTRComponent.translatable("mtr.panel.no_replays", "No replay is running")
                    : MTRComponent.translatable("mtr.panel.pick_replay", "Pick a replay to watch");
        }

        return MTRComponent.translatable("mtr.panel.status",
                "%s  ·  Tick %d / %d  ·  Step %d / %d",
                ClientReplayState.subscribed(),
                ClientReplayState.tick(), ClientReplayState.totalTick(),
                Math.max(0, ClientReplayState.cursorRow() + 1), ClientReplayState.totalRows());
    }

    private Component hintLine() {
        if (!ClientReplayState.isWatching()) {
            return MTRComponent.translatable("mtr.panel.hint_idle", "Release the key to close");
        }
        return MTRComponent.translatable("mtr.panel.hint",
                "Scroll to step %s  ·  right-click a card to go back  ·  release to close", unitLabel());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        for (Card card : buildCards()) {
            if (!MTRWidgets.isOver(event.x(), event.y(), card.x(), card.y(), card.width(), CARD_HEIGHT)) {
                continue;
            }
            Runnable action = event.button() == 1 ? card.onRight() : card.onLeft();
            if (action != null) {
                playButtonClickSound(Minecraft.getInstance().getSoundManager());
                action.run();
            }
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0 || !ClientReplayState.isWatching()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        boolean forward = scrollY > 0;
        if (MTRClientConfig.invertScroll()) forward = !forward;
        MTRClientNetworking.step(forward);
        return true;
    }
}
