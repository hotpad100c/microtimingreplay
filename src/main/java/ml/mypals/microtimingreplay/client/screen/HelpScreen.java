package ml.mypals.microtimingreplay.client.screen;

import ml.mypals.microtimingreplay.util.MTRComponent;
import ml.mypals.microtimingreplay.util.MTRHelpText;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;


@Environment(EnvType.CLIENT)
public class HelpScreen extends Screen {

    private static final int LIST_TOP = 34;
    private static final int LIST_BOTTOM_MARGIN = 30;
    private static final int SIDE_PAD = 10;
    private static final int ITEM_INDENT = 10;
    private static final int V_SCROLLBAR = 5;

    private final Screen parent;
    private List<MTRHelpText.Line> lines = List.of();
    private int scroll = 0;
    private boolean dragging = false;

    public HelpScreen(Screen parent) {
        super(MTRComponent.translatable("mtr.help.title", "MTR Guide"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<MTRHelpText.Line> all = new ArrayList<>(
                MTRHelpText.lines(MTRHelpText.TIMELINE_KEY, MTRHelpText.TIMELINE_FALLBACK));
        all.addAll(MTRHelpText.lines(MTRHelpText.COMMANDS_KEY, MTRHelpText.COMMANDS_FALLBACK));
        lines = List.copyOf(all);
        clampScroll();

        addRenderableWidget(Button.builder(
                MTRComponent.translatable("mtr.help.close", "Close"),
                b -> onClose()).bounds(this.width - 80, this.height - 24, 70, 18).build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int lineHeight() {
        return this.font.lineHeight + 2;
    }

    private int listBottom() {
        return this.height - LIST_BOTTOM_MARGIN;
    }

    private int rowsOnScreen() {
        return Math.max(1, (listBottom() - LIST_TOP) / lineHeight());
    }

    private int maxScroll() {
        return Math.max(0, lines.size() - rowsOnScreen());
    }

    private void clampScroll() {
        scroll = Math.clamp(scroll, 0, maxScroll());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.text(this.font, this.title, 8, 8, MTRWidgets.TEXT);
        graphics.text(this.font,
                MTRComponent.translatable("mtr.help.subtitle", "Screens and commands"),
                8, 20, MTRWidgets.TEXT_DIM);

        int listBottom = listBottom();
        MTRWidgets.panel(graphics, 6, LIST_TOP - 2, this.width - 12, listBottom - LIST_TOP + 4,
                MTRWidgets.PANEL_BG, MTRWidgets.PANEL_BORDER);

        graphics.enableScissor(8, LIST_TOP, this.width - 8, listBottom);
        int y = LIST_TOP;
        for (int i = scroll; i < lines.size() && y < listBottom; i++) {
            MTRHelpText.Line line = lines.get(i);
            graphics.text(this.font, Component.literal(line.text()),
                    SIDE_PAD + (line.heading() ? 0 : ITEM_INDENT), y,
                    line.heading() ? MTRWidgets.TEXT_ACCENT : MTRWidgets.TEXT);
            y += lineHeight();
        }
        graphics.disableScissor();

        drawScrollbar(graphics);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) return;

        int trackX = this.width - 6 - V_SCROLLBAR;
        int trackTop = LIST_TOP;
        int trackHeight = listBottom() - LIST_TOP;
        graphics.fill(trackX, trackTop, trackX + V_SCROLLBAR, trackTop + trackHeight, MTRWidgets.SCROLL_TRACK);

        int thumbHeight = Math.clamp((long) trackHeight * rowsOnScreen() / lines.size(), 12, trackHeight);
        int thumbY = trackTop + (trackHeight - thumbHeight) * scroll / maxScroll;
        graphics.fill(trackX, thumbY, trackX + V_SCROLLBAR, thumbY + thumbHeight, MTRWidgets.SCROLL_THUMB);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return false;
        scroll -= (int) Math.signum(scrollY) * 3;
        clampScroll();
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) return true;

        if (maxScroll() > 0 && event.x() >= this.width - 6 - V_SCROLLBAR && event.x() < this.width - 6
                && event.y() >= LIST_TOP && event.y() < listBottom()) {
            dragging = true;
            applyDrag(event.y());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            applyDrag(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private void applyDrag(double mouseY) {
        int trackHeight = listBottom() - LIST_TOP;
        if (trackHeight <= 0) return;
        double fraction = (mouseY - LIST_TOP) / trackHeight;
        scroll = (int) Math.round(fraction * maxScroll());
        clampScroll();
    }
}
