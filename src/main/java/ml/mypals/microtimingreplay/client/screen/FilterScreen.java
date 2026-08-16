package ml.mypals.microtimingreplay.client.screen;

import ml.mypals.microtimingreplay.client.ClientReplayState;
import ml.mypals.microtimingreplay.client.MTRClientNetworking;
import ml.mypals.microtimingreplay.config.RecordMode;
import ml.mypals.microtimingreplay.network.MTRPayloads;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Recording filter, one tab per category.
 *
 * <p>The state shown is the server's — toggling sends an intent and waits for the
 * server's broadcast to come back, so two admins editing at once cannot drift apart.
 */
@Environment(EnvType.CLIENT)
public class FilterScreen extends Screen {

    private static final int TAB_HEIGHT = 18;
    private static final int TAB_GAP = 3;
    private static final int ROW_HEIGHT = 20;
    private static final int LIST_TOP = 56;
    private static final int LIST_BOTTOM_MARGIN = 30;

    private String activeCategory = "";
    private int scroll = 0;

     private final Screen parent;

    public FilterScreen() {
        this(null);
    }

    public FilterScreen(Screen parent) {
        super(MTRComponent.translatable("mtr.filter.title", "Event Recording Filter"));
        this.parent = parent;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        // The panel already asked for this, but the screen can also be reached from the
        // timeline, and an empty list would be a confusing thing to land on.
        MTRClientNetworking.requestFilter();

        List<String> categories = ClientReplayState.filterCategories();
        if (activeCategory.isEmpty() && !categories.isEmpty()) {
            activeCategory = categories.getFirst();
        }

        int bottom = this.height - 24;
        addRenderableWidget(Button.builder(MTRComponent.translatable("mtr.filter.reset", "Reset to defaults"),
                b -> MTRClientNetworking.resetFilter()).bounds(8, bottom, 120, 18).build());
        addRenderableWidget(Button.builder(MTRComponent.translatable("mtr.filter.close", "Close"),
                b -> onClose()).bounds(this.width - 80, bottom, 70, 18).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<MTRPayloads.FilterRow> currentRows() {
        List<MTRPayloads.FilterRow> result = new ArrayList<>();
        for (MTRPayloads.FilterRow row : ClientReplayState.filterRows()) {
            if (row.category().equals(activeCategory)) result.add(row);
        }
        return result;
    }

    private record Tab(String category, int x, int width) {}

    private List<Tab> tabs() {
        List<Tab> tabs = new ArrayList<>();
        int x = 8;
        for (String category : ClientReplayState.filterCategories()) {
            int width = this.font.width(categoryLabel(category)) + 16;
            tabs.add(new Tab(category, x, width));
            x += width + TAB_GAP;
        }
        return tabs;
    }

    private Component categoryLabel(String category) {
        return MTRComponent.translatable("mtr.filter.category." + category.toLowerCase(), category);
    }

    private int visibleRowCount() {
        return Math.max(1, (this.height - LIST_TOP - LIST_BOTTOM_MARGIN) / ROW_HEIGHT);
    }

    private void clampScroll() {
        scroll = Math.clamp(scroll, 0, Math.max(0, currentRows().size() - visibleRowCount()));
    }

    // ── drawing ──────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(this.font, this.title, 8, 8, MTRWidgets.TEXT);
        graphics.drawString(this.font,
                MTRComponent.translatable("mtr.filter.subtitle", "Per-world config — event_filter.json"),
                8, 20, MTRWidgets.TEXT_DIM);

        for (Tab tab : tabs()) {
            boolean hovered = MTRWidgets.isOver(mouseX, mouseY, tab.x(), 32, tab.width(), TAB_HEIGHT);
            MTRWidgets.card(graphics, this.font, categoryLabel(tab.category()),
                    tab.x(), 32, tab.width(), TAB_HEIGHT, hovered, tab.category().equals(activeCategory));
        }

        int listBottom = this.height - LIST_BOTTOM_MARGIN;
        MTRWidgets.panel(graphics, 6, LIST_TOP - 2, this.width - 12, listBottom - LIST_TOP + 4,
                MTRWidgets.PANEL_BG, MTRWidgets.PANEL_BORDER);

        List<MTRPayloads.FilterRow> rows = currentRows();
        if (rows.isEmpty()) {
            graphics.drawString(this.font, MTRComponent.translatable("mtr.filter.empty", "Waiting for the server…"),
                    14, LIST_TOP + 6, MTRWidgets.TEXT_DIM);
            return;
        }

        graphics.enableScissor(8, LIST_TOP, this.width - 8, listBottom);
        // The mode column is words now, and how wide those words are depends on the language.
        int nameX = 14 + modeColumnWidth() + 10;
        int y = LIST_TOP;
        for (int i = scroll; i < rows.size() && y < listBottom; i++) {
            MTRPayloads.FilterRow row = rows.get(i);
            boolean hovered = MTRWidgets.isOver(mouseX, mouseY, 8, y, this.width - 16, ROW_HEIGHT);
            if (hovered) {
                graphics.fill(8, y, this.width - 8, y + ROW_HEIGHT, 0x30FFFFFF);
            }

            graphics.drawString(this.font, modeLabel(row.mode(), row.option()), 14, y + 6, modeColor(row.mode()));
            graphics.drawString(this.font,
                    MTRComponent.translatable("mtr.filter.event." + row.id(), row.defaultName()),
                    nameX, y + 6, row.enabled() ? MTRWidgets.TEXT : MTRWidgets.TEXT_DIM);
            graphics.drawString(this.font, Component.literal(row.id()),
                    this.width - 20 - this.font.width(row.id()), y + 6, MTRWidgets.TEXT_DIM);

            y += ROW_HEIGHT;
        }
        graphics.disableScissor();
    }

    // ── input ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        for (Tab tab : tabs()) {
            if (MTRWidgets.isOver(mouseX, mouseY, tab.x(), 32, tab.width(), TAB_HEIGHT)) {
                activeCategory = tab.category();
                scroll = 0;
                return true;
            }
        }

        int listBottom = this.height - LIST_BOTTOM_MARGIN;
        if (mouseY < LIST_TOP || mouseY >= listBottom) return false;

        int index = scroll + (int) ((mouseY - LIST_TOP) / ROW_HEIGHT);
        List<MTRPayloads.FilterRow> rows = currentRows();
        if (index < 0 || index >= rows.size()) return false;

        MTRPayloads.FilterRow row = rows.get(index);
        MTRClientNetworking.setFilter(row.id(), nextMode(row));
        return true;
    }

    /** Options are plain switches; events walk off → non-empty → all. */
    private static RecordMode nextMode(MTRPayloads.FilterRow row) {
        if (row.option()) {
            return row.mode().records() ? RecordMode.OFF : RecordMode.ALL;
        }
        return row.mode().next();
    }

    /** Widest the mode column can get, so the name column starts clear of it in any language. */
    private int modeColumnWidth() {
        int width = 0;
        for (RecordMode mode : RecordMode.values()) {
            width = Math.max(width, this.font.width(modeLabel(mode, false)));
        }
        width = Math.max(width, this.font.width(modeLabel(RecordMode.ALL, true)));
        width = Math.max(width, this.font.width(modeLabel(RecordMode.OFF, true)));
        return width;
    }

    /** Options read as a plain switch; events name which of the three modes they are in. */
    static Component modeLabel(RecordMode mode, boolean option) {
        if (option) {
            return mode.records()
                    ? MTRComponent.translatable("mtr.filter.mode.on", "On")
                    : MTRComponent.translatable("mtr.filter.mode.disabled", "Off");
        }
        return switch (mode) {
            case OFF -> MTRComponent.translatable("mtr.filter.mode.off", "DontRecord");
            case NO_DISPLAY -> MTRComponent.translatable("mtr.filter.mode.no_display", "Hidden");
            case NON_EMPTY -> MTRComponent.translatable("mtr.filter.mode.non_empty", "NotEmpty");
            case ALL -> MTRComponent.translatable("mtr.filter.mode.all", "Everything");
        };
    }

    private static int modeColor(RecordMode mode) {
        return switch (mode) {
            case OFF -> MTRWidgets.TEXT_OFF;
            case NO_DISPLAY -> MTRWidgets.TEXT_DIM;
            case NON_EMPTY -> MTRWidgets.TEXT_ACCENT;
            case ALL -> MTRWidgets.TEXT_ON;
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return false;
        scroll -= (int) Math.signum(scrollY) * 2;
        clampScroll();
        return true;
    }
}
