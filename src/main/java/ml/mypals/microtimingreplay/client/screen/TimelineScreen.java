package ml.mypals.microtimingreplay.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;
import ml.mypals.microtimingreplay.client.ClientReplayState;
import ml.mypals.microtimingreplay.client.MTRClientConfig;
import ml.mypals.microtimingreplay.client.MTRClientNetworking;
import ml.mypals.microtimingreplay.network.MTRPayloads;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetTooltipHolder;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import static ml.mypals.microtimingreplay.replay.dialog.StackTraceScreenGenerator.formatStackTraceLine;


@Environment(EnvType.CLIENT)
public class TimelineScreen extends Screen {

    private static final int ROW_HEIGHT = 11;
    private static final int LIST_TOP = 44;
    private static final int LIST_BOTTOM_MARGIN = 30;
    private static final int INDENT = 9;

    /**
     * Column width at GUI scale 1, narrowing by {@link #WIDTH_PER_SCALE} for each step up.
     * Coordinates here are GUI units, which already shrink as the scale rises, so without
     * this the columns would eat the whole screen at scale 3 and up.
     */
    private static final int WIDTH_AT_SCALE_1 = 400;
    private static final int WIDTH_PER_SCALE = 100;
    /**
     * Vanilla offers as many scale steps as the window can fit — {@code Window.calculateScale}
     * keeps going while the framebuffer still divides into at least 320x240 — so a 4K
     * display reaches scale 9 and the ramp above would go negative. Floor it.
     */
    private static final int MIN_PANEL_WIDTH = 100;
    /** The gap between the two columns is reserved space, so never let them close it. */
    private static final int MIN_MIDDLE_GAP = 100;

    private static final int LIST_X = 0;
    private static final int EDGE_PAD = 0;
    private static final int V_SCROLLBAR = 5;
    private static final int H_SCROLLBAR = 4;
    /** Width of the right-aligned "#n↗" jump hit box, which never scrolls sideways. */
    private static final int JUMP_ZONE = 46;

    /** How many pixels one notch of horizontal scroll moves the rows. */
    private static final int H_SCROLL_STEP = 16;

    /** Lines of call stack the hover tooltip shows before it starts summarising. */
    private static final int TOOLTIP_TRACE_LINES = 20;

    /**
     * Largest share of the detail column the summary may take before it starts scrolling
     * inside itself. {@code SetBlockEvent} alone reports a state diff plus nine flag bits,
     * so without a ceiling it would push the call stack off the bottom.
     */
    private static final double SUMMARY_MAX_SHARE = 0.5;

    private final Set<Integer> collapsed = new HashSet<>();
    private List<Integer> visible = new ArrayList<>();

    /** Shared by the list and the detail column, so the two read as a matched pair. */
    private int panelWidth = WIDTH_AT_SCALE_1;

    private int scroll = 0;
    private int hScroll = 0;
    /** Widest row drawn last frame; what {@link #hScroll} is clamped against. */
    private int measuredContentWidth = 0;

    private int selectedStep = -1;
    private int lastRevision = -1;
    private int lastCursorRow = -1;

    private int detailScroll = 0;
    private int detailHScroll = 0;
    private int summaryScroll = 0;

    private enum Drag { NONE, TIMELINE_V, TIMELINE_H, DETAIL_V, DETAIL_H, SUMMARY_V }

    private Drag dragging = Drag.NONE;

    private final WidgetTooltipHolder stackTooltip = new WidgetTooltipHolder();
    /** Hit box of the call-stack header, stamped while drawing and read on click. */
    private ScreenRectangle stackHeaderRect = ScreenRectangle.empty();
    private long copiedAtMillis = 0;

    private Button followButton, cameraFollowButton;

    public TimelineScreen() {
        super(MTRComponent.translatable("mtr.timeline.title", "MTR Timeline"));
    }

    private List<MTRPayloads.TimelineRow> rows() {
        return ClientReplayState.rows();
    }

    /**
     * Recomputed in {@link #init()}, which vanilla also calls on resize — so changing the
     * GUI scale in the options screen and coming back lands on the right width.
     */
    private int computePanelWidth() {
        int scale = Math.max(1, this.minecraft.getWindow().getGuiScale());
        int preferred = Math.max(MIN_PANEL_WIDTH, WIDTH_AT_SCALE_1 - (scale - 1) * WIDTH_PER_SCALE);

        // A small window at scale 1 can be narrower than two preferred columns.
        int fits = (this.width - LIST_X - EDGE_PAD - MIN_MIDDLE_GAP) / 2;
        return Math.max(1, Math.min(preferred, fits));
    }

    @Override
    protected void init() {
        panelWidth = computePanelWidth();
        rebuildVisible();
        lastRevision = ClientReplayState.timelineRevision();

        // Land on whatever the replay is sitting on rather than an empty detail column.
        lastCursorRow = ClientReplayState.cursorRow();
        selectStep(ClientReplayState.currentStep());

        int x = 8;
        int y = 22;
        x += addToolButton(MTRComponent.translatable("mtr.timeline.expand_all", "Expand all"), x, y, 74,
                () -> { collapsed.clear(); rebuildVisible(); });
        x += addToolButton(MTRComponent.translatable("mtr.timeline.collapse_all", "Collapse all"), x, y, 84,
                () -> collapseToLevel(0));
        for (int level = 1; level <= 3; level++) {
            int target = level;
            x += addToolButton(Component.literal("L" + level), x, y, 26, () -> collapseToLevel(target));
        }

        followButton = Button.builder(followLabel(), button -> {
            MTRClientConfig.setFollowCursor(!MTRClientConfig.followCursor());
            button.setMessage(followLabel());
        }).bounds(x, y, 92, 16).build();
        addRenderableWidget(followButton);

        cameraFollowButton = Button.builder(cameraFollow(), button -> {
            MTRClientNetworking.setCameraFollow(!ClientReplayState.cameraFollow());
            button.setMessage(cameraFollow());
        }).bounds(followButton.getWidth() + x + 4, y, 92, 16).build();
        addRenderableWidget(cameraFollowButton);

        int bottom = this.height - 24;
        addRenderableWidget(Button.builder(MTRComponent.translatable("mtr.timeline.backward", "◀ Back"),
                b -> MTRClientNetworking.step(false)).bounds(8, bottom, 70, 18).build());
        addRenderableWidget(Button.builder(MTRComponent.translatable("mtr.timeline.forward", "Forward ▶"),
                b -> MTRClientNetworking.step(true)).bounds(82, bottom, 70, 18).build());
        addRenderableWidget(Button.builder(MTRComponent.translatable("mtr.timeline.filter", "Filter"),
                b -> this.minecraft.setScreen(new FilterScreen())).bounds(this.width - 154, bottom, 70, 18).build());
        addRenderableWidget(Button.builder(MTRComponent.translatable("mtr.timeline.close", "Close"),
                b -> onClose()).bounds(this.width - 80, bottom, 70, 18).build());
    }

    private int addToolButton(Component label, int x, int y, int width, Runnable action) {
        addRenderableWidget(Button.builder(label, b -> action.run()).bounds(x, y, width, 16).build());
        return width + 4;
    }

    private Component followLabel() {
        return MTRComponent.translatable(
                MTRClientConfig.followCursor() ? "mtr.timeline.follow_on" : "mtr.timeline.follow_off",
                MTRClientConfig.followCursor() ? "Follow: on" : "Follow: off");
    }

    private Component cameraFollow() {
        return MTRComponent.translatable(
                ClientReplayState.cameraFollow() ? "mtr.timeline.camera_follow_on" : "mtr.timeline.camera_follow_off",
                ClientReplayState.cameraFollow() ? "CameraFollow: on" : "CameraFollow: off");
    }

    private int listRight() {
        return LIST_X + panelWidth;
    }

    /** Right edge of row content, i.e. inside the vertical scrollbar. */
    private int contentRight() {
        return listRight() - V_SCROLLBAR - 2;
    }

    /** Right edge of the sideways-scrolling label region, before the pinned jump box. */
    private int labelRight() {
        return contentRight() - JUMP_ZONE;
    }

    private int labelLeft() {
        return LIST_X + 6;
    }

    private int detailX() {
        return this.width - panelWidth - EDGE_PAD;
    }

    private int listBottom() {
        return this.height - LIST_BOTTOM_MARGIN;
    }

    /**
     * Where rows stop. The horizontal bar's strip is reserved unconditionally rather than
     * only when scrolling is possible, so the last row does not jump as content width changes.
     */
    private int rowsBottom() {
        return listBottom() - H_SCROLLBAR - 2;
    }

    private int hScrollbarY() {
        return rowsBottom() + 1;
    }

    private int hTrackLeft() {
        return LIST_X + 1;
    }

    private int hTrackWidth() {
        return labelRight() - hTrackLeft();
    }

    // The detail column mirrors the list: same chrome, same row metrics, same scrollbar.

    private int detailContentRight() {
        return detailX() + panelWidth - V_SCROLLBAR - 2;
    }

    private int detailSummaryTop() {
        return LIST_TOP + ROW_HEIGHT + 2;
    }

    private int detailSummaryLines() {
        return cachedView == null || cachedStep != selectedStep ? 0 : cachedView.summary().size();
    }

    /** What the summary would take if nothing limited it, less the header and gaps. */
    private int detailSummaryMaxHeight() {
        int available = detailBodyBottom() - detailSummaryTop() - ROW_HEIGHT - 6;
        return Math.max(ROW_HEIGHT, (int) (available * SUMMARY_MAX_SHARE));
    }

    private int detailSummaryHeight() {
        return Math.min(detailSummaryLines() * ROW_HEIGHT, detailSummaryMaxHeight());
    }

    private int detailSummaryBottom() {
        return detailSummaryTop() + detailSummaryHeight();
    }

    private int detailSummaryRowCount() {
        return Math.max(1, detailSummaryHeight() / ROW_HEIGHT);
    }

    /** The pinned call-stack header, sitting under whatever the summary was allowed. */
    private int detailHeaderY() {
        return detailSummaryBottom() + 4;
    }

    /** First row of the scrolling body, which holds the call stack and nothing else. */
    private int detailBodyTop() {
        return detailHeaderY() + ROW_HEIGHT + 2;
    }

    /** Mirrors {@link #rowsBottom()}: the horizontal bar's strip is always reserved. */
    private int detailBodyBottom() {
        return listBottom() - H_SCROLLBAR - 2;
    }

    private int detailBodyRowCount() {
        return Math.max(1, (detailBodyBottom() - detailBodyTop()) / ROW_HEIGHT);
    }

    private int detailContentLeft() {
        return detailX() + 6;
    }

    private int detailHScrollbarY() {
        return detailBodyBottom() + 1;
    }

    private int detailHTrackLeft() {
        return detailX() + 1;
    }

    private int detailHTrackWidth() {
        return detailContentRight() - detailHTrackLeft();
    }

    private int detailContentWidth() {
        return cachedView == null || cachedStep != selectedStep ? 0 : cachedView.contentWidth();
    }

    /**
     * Unlike the timeline, this maximum is exact: a call stack runs to a few hundred lines
     * at most, so every line is measured when the view is built rather than estimated from
     * whatever is on screen — the bar does not resize as you scroll down.
     */
    private int maxDetailHScroll() {
        return Math.max(0, detailContentWidth() - (detailContentRight() - detailContentLeft()));
    }

    /** True when the next row nests deeper, i.e. this scope actually contains something. */
    private boolean hasChildren(int index) {
        List<MTRPayloads.TimelineRow> rows = rows();
        return index + 1 < rows.size() && rows.get(index + 1).depth() > rows.get(index).depth();
    }

    private void collapseToLevel(int level) {
        collapsed.clear();
        List<MTRPayloads.TimelineRow> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).depth() >= level && hasChildren(i)) {
                collapsed.add(i);
            }
        }
        rebuildVisible();
    }

    private void rebuildVisible() {
        List<MTRPayloads.TimelineRow> rows = rows();
        List<Integer> result = new ArrayList<>();

        int i = 0;
        while (i < rows.size()) {
            result.add(i);
            if (collapsed.contains(i)) {
                // Skip the whole subtree: everything after it that nests deeper.
                int depth = rows.get(i).depth();
                int j = i + 1;
                while (j < rows.size() && rows.get(j).depth() > depth) j++;
                i = j;
            } else {
                i++;
            }
        }

        visible = result;
        clampScroll();
    }

    private int visibleRowCount() {
        return Math.max(1, (rowsBottom() - LIST_TOP) / ROW_HEIGHT);
    }

    private void clampScroll() {
        scroll = Math.clamp(scroll, 0, Math.max(0, visible.size() - visibleRowCount()));
        hScroll = Math.clamp(hScroll, 0, maxHScroll());
    }

    /**
     * Measured from the rows actually on screen rather than all of them: with six figures
     * of rows, running {@code font.width} over every label would cost more than the whole
     * rest of the frame.
     */
    private int maxHScroll() {
        return Math.max(0, measuredContentWidth - (labelRight() - labelLeft()));
    }

    /** Where {@code rowIndex} shows up in the folded view — its nearest visible ancestor. */
    private int visibleIndexOf(int rowIndex) {
        int low = 0;
        int high = visible.size() - 1;
        int best = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (visible.get(mid) <= rowIndex) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private void selectStep(int step) {
        if (step < 0 || step == selectedStep) return;

        selectedStep = step;
        // A new step is new content; carrying the old scroll offsets into it is meaningless.
        summaryScroll = 0;
        detailScroll = 0;
        detailHScroll = 0;

        if (ClientReplayState.details(step) == null) {
            MTRClientNetworking.requestDetails(step);
        }
    }
    @Override
    public void tick() {
        if (ClientReplayState.timelineRevision() != lastRevision) {
            lastRevision = ClientReplayState.timelineRevision();
            collapsed.clear();
            rebuildVisible();
        }

        int cursorRow = ClientReplayState.cursorRow();
        if (cursorRow != lastCursorRow) {
            lastCursorRow = cursorRow;
            // Stepping should carry the detail column along with it; that is the point
            // of stepping. A manual pick only survives until the replay moves again.
            selectStep(ClientReplayState.currentStep());
            if (MTRClientConfig.followCursor()) {
                scrollTo(cursorRow);
            }
        }
    }

    private void scrollTo(int rowIndex) {
        int target = visibleIndexOf(rowIndex);
        if (target < 0) return;

        int rowsOnScreen = visibleRowCount();
        if (target < scroll || target >= scroll + rowsOnScreen) {
            scroll = target - rowsOnScreen / 2;
        }
        clampScroll();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }


    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Everything the default does is screen-wide paint we do not want — except this,
        // which is the accessibility sound subtitles and has nothing to do with the background.
        this.minecraft.gui.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int listBottom = listBottom();

        graphics.fill(0, 0, this.width, LIST_TOP - 2, MTRWidgets.CHROME_BG);
        graphics.fill(0, listBottom + 2, this.width, this.height, MTRWidgets.CHROME_BG);

        graphics.text(this.font, headerLine(), 8, 8, MTRWidgets.TEXT);

        MTRWidgets.panel(graphics, LIST_X, LIST_TOP - 2, panelWidth, listBottom - LIST_TOP + 4,
                MTRWidgets.PANEL_BG, MTRWidgets.PANEL_BORDER);

        if (rows().isEmpty()) {
            graphics.text(this.font,
                    MTRComponent.translatable("mtr.timeline.empty", "No timeline — watch a running replay first"),
                    LIST_X + 8, LIST_TOP + 6, MTRWidgets.TEXT_DIM);
        } else {
            graphics.enableScissor(LIST_X + 1, LIST_TOP, contentRight(), rowsBottom());
            drawRows(graphics, mouseX, mouseY, rowsBottom());
            graphics.disableScissor();

            drawScrollbars(graphics);
        }

        drawDetails(graphics, mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int listBottom) {
        List<MTRPayloads.TimelineRow> rows = rows();
        int cursorRow = ClientReplayState.cursorRow();
        int labelRight = labelRight();
        int contentRight = contentRight();

        long previousTick = Long.MIN_VALUE;
        if (scroll > 0) {
            previousTick = rows.get(visible.get(scroll - 1)).tick();
        }

        int widest = 0;
        int y = LIST_TOP;
        for (int i = scroll; i < visible.size() && y < listBottom; i++) {
            int rowIndex = visible.get(i);
            MTRPayloads.TimelineRow row = rows.get(rowIndex);

            if (row.tick() != previousTick) {
                if (y + ROW_HEIGHT > listBottom) break;
                graphics.text(this.font,
                        MTRComponent.translatable("mtr.timeline.tick_marker", "── Tick %d ──", row.tick()),
                        labelLeft(), y + 1, MTRWidgets.TEXT_DIM);
                previousTick = row.tick();
                y += ROW_HEIGHT;
                if (y >= listBottom) break;
            }

            boolean hovered = MTRWidgets.isOver(mouseX, mouseY, LIST_X + 1, y, contentRight - LIST_X, ROW_HEIGHT);
            if (row.step() == selectedStep) {
                graphics.fill(LIST_X + 1, y, contentRight, y + ROW_HEIGHT, MTRWidgets.CARD_BG_ACTIVE);
            }
            if (rowIndex == cursorRow) {
                graphics.fill(LIST_X + 1, y, contentRight, y + ROW_HEIGHT, MTRWidgets.CURSOR_ROW_BG);
            } else if (hovered) {
                graphics.fill(LIST_X + 1, y, contentRight, y + ROW_HEIGHT, 0x30FFFFFF);
            }

            int contentWidth = rowIndent(row) + 20 + this.font.width(row.label());
            if (contentWidth > widest) widest = contentWidth;

            graphics.enableScissor(LIST_X + 1, y, labelRight, y + ROW_HEIGHT);
            int x = labelLeft() + rowIndent(row) - hScroll;
            if (hasChildren(rowIndex)) {
                graphics.text(this.font, Component.literal(collapsed.contains(rowIndex) ? "[+]" : "[-]"),
                        x, y + 1, MTRWidgets.TEXT_ACCENT);
            }
            graphics.text(this.font, row.label(), x + 20, y + 1, MTRWidgets.opaque(row.color()));
            graphics.disableScissor();

            Component jump = Component.literal("#" + row.step() + "↗");
            graphics.text(this.font, jump, contentRight - 2 - this.font.width(jump), y + 1,
                    row.step() == selectedStep ? MTRWidgets.TEXT_ACCENT : MTRWidgets.TEXT_DIM);

            y += ROW_HEIGHT;
        }

        measuredContentWidth = widest;
        if (hScroll > maxHScroll()) hScroll = maxHScroll();
    }

    private int rowIndent(MTRPayloads.TimelineRow row) {
        return row.depth() * INDENT;
    }

    private void drawScrollbars(GuiGraphicsExtractor graphics) {
        int rowsOnScreen = visibleRowCount();
        int right = listRight();
        int rowsBottom = rowsBottom();

        if (visible.size() > rowsOnScreen) {
            int trackHeight = rowsBottom - LIST_TOP;
            int thumbHeight = verticalThumbHeight(trackHeight, rowsOnScreen, visible.size());
            int travel = trackHeight - thumbHeight;
            int thumbY = LIST_TOP + (travel * scroll / Math.max(1, visible.size() - rowsOnScreen));

            graphics.fill(right - V_SCROLLBAR, LIST_TOP, right - 2, rowsBottom, MTRWidgets.SCROLL_TRACK);
            graphics.fill(right - V_SCROLLBAR, thumbY, right - 2, thumbY + thumbHeight,
                    dragging == Drag.TIMELINE_V ? MTRWidgets.SCROLL_THUMB_ACTIVE : MTRWidgets.SCROLL_THUMB);
        }

        int maxH = maxHScroll();
        if (maxH > 0) {
            int trackLeft = hTrackLeft();
            int trackWidth = hTrackWidth();
            int thumbWidth = horizontalThumbWidth(trackWidth);
            int travel = trackWidth - thumbWidth;
            int thumbX = trackLeft + (travel * hScroll / maxH);
            int barY = hScrollbarY();

            graphics.fill(trackLeft, barY, trackLeft + trackWidth, barY + H_SCROLLBAR, MTRWidgets.SCROLL_TRACK);
            graphics.fill(thumbX, barY, thumbX + thumbWidth, barY + H_SCROLLBAR,
                    dragging == Drag.TIMELINE_H ? MTRWidgets.SCROLL_THUMB_ACTIVE : MTRWidgets.SCROLL_THUMB);
        }
    }

    private static int verticalThumbHeight(int trackHeight, int rowsOnScreen, int totalRows) {
        return Math.clamp((long) trackHeight * rowsOnScreen / Math.max(1, totalRows), 12, trackHeight);
    }

    private int horizontalThumbWidth(int trackWidth) {
        return Math.clamp((long) trackWidth * trackWidth / Math.max(1, measuredContentWidth), 16, trackWidth);
    }

    private record DetailLine(FormattedCharSequence text, int color) {}

    /**
     * Everything the detail column needs, prepared once per selection instead of per
     * frame — wrapping the summary and colourising a deep call stack is far too much work
     * to redo 60 times a second for a panel whose contents cannot change between frames.
     */
    /**
     * @param summary      the event's own information, wrapped and pinned above the header
     * @param trace        the call stack, and the only thing that scrolls
     * @param contentWidth widest trace line, so it excludes the wrapped summary
     */
    private record DetailView(List<DetailLine> summary, List<DetailLine> trace, int contentWidth,
                              Component tooltip, String copyText) {}

    private DetailView cachedView;
    private int cachedStep = -1;
    /** Column width the cached lines were trimmed to; a resize or scale change invalidates them. */
    private int cachedPanelWidth = -1;

    private DetailView detailView(MTRPayloads.DetailsS2C details) {
        if (cachedView != null && cachedStep == selectedStep && cachedPanelWidth == panelWidth) {
            return cachedView;
        }

        int inner = panelWidth - V_SCROLLBAR - 12;

        List<DetailLine> summary = new ArrayList<>();
        for (FormattedCharSequence line : this.font.split(details.hover(), inner)) {
            summary.add(new DetailLine(line, MTRWidgets.TEXT));
        }

        // Call-stack frames keep their full width and are reached by scrolling sideways —
        // a frame split across two lines is unreadable, and truncating one loses the very
        // part that identifies it.
        List<DetailLine> trace = new ArrayList<>(details.stackTrace().size());
        int contentWidth = 0;
        for (String raw : details.stackTrace()) {
            DetailLine line = new DetailLine(formatStackTraceLine(raw).getVisualOrderText(), MTRWidgets.TEXT_DIM);
            trace.add(line);
            contentWidth = Math.max(contentWidth, this.font.width(line.text()));
        }

        MutableComponent tooltip = MTRComponent.translatable(
                        "mtr.stacktrace.tooltip_title", "Step #%d StackTrace:\n", selectedStep)
                .withStyle(ChatFormatting.GOLD);

        int shown = Math.min(details.stackTrace().size(), TOOLTIP_TRACE_LINES);
        for (int i = 0; i < shown; i++) {
            tooltip.append(formatStackTraceLine(details.stackTrace().get(i)))
                    .append(Component.literal(i < shown - 1 ? "\n" : ""));
        }
        if (details.stackTrace().size() > shown) {
            tooltip.append(Component.literal("\n"))
                    .append(MTRComponent.translatable("mtr.stacktrace.tooltip_more", "... and %d more lines",
                                    details.stackTrace().size() - shown)
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }

        cachedView = new DetailView(summary, trace, contentWidth, tooltip,
                String.join("\n", details.stackTrace()));
        cachedStep = selectedStep;
        cachedPanelWidth = panelWidth;
        return cachedView;
    }

    private void drawDetails(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = detailX();
        int listBottom = listBottom();
        MTRWidgets.panel(graphics, x, LIST_TOP - 2, panelWidth, listBottom - LIST_TOP + 4,
                MTRWidgets.PANEL_BG, MTRWidgets.PANEL_BORDER);

        stackHeaderRect = ScreenRectangle.empty();

        if (selectedStep < 0) {
            graphics.text(this.font,
                    MTRComponent.translatable("mtr.timeline.no_selection", "Select a row to inspect it"),
                    x + 6, LIST_TOP + 1, MTRWidgets.TEXT_DIM);
            return;
        }

        graphics.text(this.font, MTRComponent.translatable("mtr.timeline.details", "Step #%d", selectedStep),
                x + 6, LIST_TOP + 1, MTRWidgets.TEXT_ACCENT);

        MTRPayloads.DetailsS2C details = ClientReplayState.details(selectedStep);
        if (details == null) {
            graphics.text(this.font, MTRComponent.translatable("mtr.timeline.loading", "Loading…"),
                    x + 6, LIST_TOP + ROW_HEIGHT + 1, MTRWidgets.TEXT_DIM);
            return;
        }

        DetailView view = detailView(details);

        clampSummaryScroll();

        // The event's own information, in its own scroll region above the call stack.
        int summaryTop = detailSummaryTop();
        int summaryBottom = detailSummaryBottom();
        graphics.enableScissor(x + 1, summaryTop, detailContentRight(), summaryBottom);
        int summaryY = summaryTop;
        for (int i = summaryScroll; i < view.summary().size() && summaryY < summaryBottom; i++) {
            DetailLine line = view.summary().get(i);
            graphics.text(this.font, line.text(), x + 6, summaryY + 1, line.color());
            summaryY += ROW_HEIGHT;
        }
        graphics.disableScissor();
        drawSummaryScrollbar(graphics, view.summary().size(), summaryTop, summaryBottom);

        int headerY = detailHeaderY();
        Component header = copiedRecently()
                ? MTRComponent.translatable("mtr.timeline.stacktrace_copied", "Call stack — copied!")
                : MTRComponent.translatable("mtr.timeline.stacktrace", "Call stack ⧉");
        stackHeaderRect = new ScreenRectangle(x + 6, headerY - 1, panelWidth - 12, this.font.lineHeight + 2);

        boolean headerHovered = MTRWidgets.isOver(mouseX, mouseY,
                stackHeaderRect.left(), stackHeaderRect.top(), stackHeaderRect.width(), stackHeaderRect.height());
        if (headerHovered) {
            graphics.fill(stackHeaderRect.left(), stackHeaderRect.top(),
                    stackHeaderRect.left() + stackHeaderRect.width(),
                    stackHeaderRect.top() + stackHeaderRect.height(), 0x30FFFFFF);
        }
        graphics.text(this.font, header, x + 6, headerY,
                copiedRecently() ? MTRWidgets.TEXT_ON : MTRWidgets.TEXT_ACCENT);

        clampDetailScroll();

        // Same row metrics and clipping as the timeline list, so the two columns match.
        int bodyTop = detailBodyTop();
        int bodyBottom = detailBodyBottom();
        graphics.enableScissor(x + 1, bodyTop, detailContentRight(), bodyBottom);
        int lineY = bodyTop;
        for (int i = detailScroll; i < view.trace().size() && lineY < bodyBottom; i++) {
            DetailLine line = view.trace().get(i);
            graphics.text(this.font, line.text(), detailContentLeft() - detailHScroll, lineY + 1, line.color());
            lineY += ROW_HEIGHT;
        }
        graphics.disableScissor();

        drawDetailScrollbars(graphics, view.trace().size(), bodyBottom);

        // Vanilla draws this during the outer render pass, so it lands above everything.
        stackTooltip.set(Tooltip.create(view.tooltip()));
        stackTooltip.refreshTooltipForNextRenderPass(graphics, mouseX, mouseY, headerHovered, false, stackHeaderRect);
    }

    private int detailLineCount() {
        return cachedView == null || cachedStep != selectedStep ? 0 : cachedView.trace().size();
    }

    private void clampDetailScroll() {
        detailScroll = Math.clamp(detailScroll, 0, Math.max(0, detailLineCount() - detailBodyRowCount()));
        detailHScroll = Math.clamp(detailHScroll, 0, maxDetailHScroll());
    }

    private void clampSummaryScroll() {
        summaryScroll = Math.clamp(summaryScroll, 0, Math.max(0, detailSummaryLines() - detailSummaryRowCount()));
    }

    private void drawSummaryScrollbar(GuiGraphicsExtractor graphics, int lineCount, int top, int bottom) {
        int rowsOnScreen = detailSummaryRowCount();
        if (lineCount <= rowsOnScreen) return;

        int right = detailX() + panelWidth;
        int trackHeight = bottom - top;
        int thumbHeight = verticalThumbHeight(trackHeight, rowsOnScreen, lineCount);
        int travel = trackHeight - thumbHeight;
        int thumbY = top + (travel * summaryScroll / Math.max(1, lineCount - rowsOnScreen));

        graphics.fill(right - V_SCROLLBAR, top, right - 2, bottom, MTRWidgets.SCROLL_TRACK);
        graphics.fill(right - V_SCROLLBAR, thumbY, right - 2, thumbY + thumbHeight,
                dragging == Drag.SUMMARY_V ? MTRWidgets.SCROLL_THUMB_ACTIVE : MTRWidgets.SCROLL_THUMB);
    }

    private void drawDetailScrollbars(GuiGraphicsExtractor graphics, int lineCount, int bodyBottom) {
        int rowsOnScreen = detailBodyRowCount();
        if (lineCount > rowsOnScreen) {
            int right = detailX() + panelWidth;
            int top = detailBodyTop();
            int trackHeight = bodyBottom - top;
            int thumbHeight = verticalThumbHeight(trackHeight, rowsOnScreen, lineCount);
            int travel = trackHeight - thumbHeight;
            int thumbY = top + (travel * detailScroll / Math.max(1, lineCount - rowsOnScreen));

            graphics.fill(right - V_SCROLLBAR, top, right - 2, bodyBottom, MTRWidgets.SCROLL_TRACK);
            graphics.fill(right - V_SCROLLBAR, thumbY, right - 2, thumbY + thumbHeight,
                    dragging == Drag.DETAIL_V ? MTRWidgets.SCROLL_THUMB_ACTIVE : MTRWidgets.SCROLL_THUMB);
        }

        int maxH = maxDetailHScroll();
        if (maxH > 0) {
            int trackLeft = detailHTrackLeft();
            int trackWidth = detailHTrackWidth();
            int thumbWidth = Math.clamp((long) trackWidth * trackWidth / Math.max(1, detailContentWidth()),
                    16, trackWidth);
            int travel = trackWidth - thumbWidth;
            int thumbX = trackLeft + (travel * detailHScroll / maxH);
            int barY = detailHScrollbarY();

            graphics.fill(trackLeft, barY, trackLeft + trackWidth, barY + H_SCROLLBAR, MTRWidgets.SCROLL_TRACK);
            graphics.fill(thumbX, barY, thumbX + thumbWidth, barY + H_SCROLLBAR,
                    dragging == Drag.DETAIL_H ? MTRWidgets.SCROLL_THUMB_ACTIVE : MTRWidgets.SCROLL_THUMB);
        }
    }

    private boolean copiedRecently() {
        return System.currentTimeMillis() - copiedAtMillis < 1200;
    }

    private Component headerLine() {
        if (!ClientReplayState.isWatching()) {
            return MTRComponent.translatable("mtr.timeline.not_watching", "Not watching a replay");
        }
        return MTRComponent.translatable("mtr.timeline.header",
                "%s  ·  Tick %d / %d  ·  Step %d / %d  ·  %d rows (%d shown)",
                ClientReplayState.subscribed(),
                ClientReplayState.tick(), ClientReplayState.totalTick(),
                Math.max(0, ClientReplayState.cursorRow() + 1), ClientReplayState.totalRows(),
                rows().size(), visible.size());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) return true;

        // Scrollbars first: they sit on top of the lists they belong to.
        if (overTimelineVBar(event.x(), event.y())) {
            dragging = Drag.TIMELINE_V;
            applyTimelineVDrag(event.y());
            return true;
        }
        if (overTimelineHBar(event.x(), event.y())) {
            dragging = Drag.TIMELINE_H;
            applyTimelineHDrag(event.x());
            return true;
        }
        if (overSummaryVBar(event.x(), event.y())) {
            dragging = Drag.SUMMARY_V;
            applySummaryVDrag(event.y());
            return true;
        }
        if (overDetailVBar(event.x(), event.y())) {
            dragging = Drag.DETAIL_V;
            applyDetailVDrag(event.y());
            return true;
        }
        if (overDetailHBar(event.x(), event.y())) {
            dragging = Drag.DETAIL_H;
            applyDetailHDrag(event.x());
            return true;
        }

        if (MTRWidgets.isOver(event.x(), event.y(), stackHeaderRect.left(), stackHeaderRect.top(),
                stackHeaderRect.width(), stackHeaderRect.height())) {
            DetailView view = cachedView;
            if (view != null) {
                this.minecraft.keyboardHandler.setClipboard(view.copyText());
                copiedAtMillis = System.currentTimeMillis();
            }
            return true;
        }

        int rowsBottom = rowsBottom();
        if (event.y() < LIST_TOP || event.y() >= rowsBottom
                || event.x() < LIST_X || event.x() >= contentRight()) {
            return false;
        }

        int rowIndex = rowAt(event.y(), rowsBottom);
        if (rowIndex < 0) return false;

        MTRPayloads.TimelineRow row = rows().get(rowIndex);

        // The jump box is pinned, so it is tested before anything that scrolls.
        if (event.x() >= contentRight() - JUMP_ZONE) {
            MTRClientNetworking.jump(row.step());
            return true;
        }

        int toggleX = labelLeft() + rowIndent(row) - hScroll;
        if (hasChildren(rowIndex) && event.x() >= toggleX && event.x() < toggleX + 20) {
            if (!collapsed.remove(rowIndex)) {
                collapsed.add(rowIndex);
            }
            rebuildVisible();
            return true;
        }

        selectStep(row.step());
        if (doubled) {
            MTRClientNetworking.jump(row.step());
        }
        return true;
    }

    /**
     * Reverses the draw loop's layout, tick separators included — they occupy a row of
     * their own, so a naive divide would select the wrong event.
     */
    private int rowAt(double mouseY, int listBottom) {
        List<MTRPayloads.TimelineRow> rows = rows();

        long previousTick = Long.MIN_VALUE;
        if (scroll > 0) {
            previousTick = rows.get(visible.get(scroll - 1)).tick();
        }

        int y = LIST_TOP;
        for (int i = scroll; i < visible.size() && y < listBottom; i++) {
            int rowIndex = visible.get(i);
            MTRPayloads.TimelineRow row = rows.get(rowIndex);

            if (row.tick() != previousTick) {
                if (y + ROW_HEIGHT > listBottom) break;
                previousTick = row.tick();
                y += ROW_HEIGHT;
                if (y >= listBottom) break;
            }

            if (mouseY >= y && mouseY < y + ROW_HEIGHT) return rowIndex;
            y += ROW_HEIGHT;
        }
        return -1;
    }

    /**
     * {@code mouseScrolled} carries no modifier state in 26.1 — modifiers only ride on
     * key and button events — so ask the window whether the key is physically down.
     */
    private boolean isKeyHeld(int left, int right) {
        return InputConstants.isKeyDown(this.minecraft.getWindow(), left)
                || InputConstants.isKeyDown(this.minecraft.getWindow(), right);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // A tilt wheel scrolls sideways directly; control plus the normal wheel is the
        // fallback for the mice that do not have one.
        if (scrollX != 0) {
            if (mouseX >= detailX()) {
                detailHScroll -= (int) Math.signum(scrollX) * H_SCROLL_STEP;
                clampDetailScroll();
            } else {
                hScroll -= (int) Math.signum(scrollX) * H_SCROLL_STEP;
                clampScroll();
            }
            return true;
        }

        if (scrollY == 0) return false;

        if (isKeyHeld(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            // Shift turns the wheel into replay control, matching the hotbar panel.
            boolean forward = scrollY > 0;
            if (MTRClientConfig.invertScroll()) forward = !forward;
            MTRClientNetworking.step(forward);
            return true;
        }

        // Whichever column the pointer is over is the one that scrolls, and control turns
        // the wheel sideways in either of them.
        if (mouseX >= detailX()) {
            // The summary is a region of its own, so the wheel follows the pointer into it.
            if (mouseY < detailSummaryBottom()) {
                summaryScroll -= (int) Math.signum(scrollY) * 3;
                clampSummaryScroll();
                return true;
            }
            if (isKeyHeld(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
                detailHScroll -= (int) Math.signum(scrollY) * H_SCROLL_STEP;
            } else {
                detailScroll -= (int) Math.signum(scrollY) * 3;
            }
            clampDetailScroll();
            return true;
        }

        if (isKeyHeld(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
            hScroll -= (int) Math.signum(scrollY) * H_SCROLL_STEP;
            clampScroll();
            return true;
        }

        scroll -= (int) Math.signum(scrollY) * 3;
        clampScroll();
        return true;
    }

    // ── scrollbar dragging ───────────────────────────────────────────────────

    private boolean overTimelineVBar(double mouseX, double mouseY) {
        if (visible.size() <= visibleRowCount()) return false;
        return MTRWidgets.isOver(mouseX, mouseY, listRight() - V_SCROLLBAR, LIST_TOP,
                V_SCROLLBAR, rowsBottom() - LIST_TOP);
    }

    private boolean overTimelineHBar(double mouseX, double mouseY) {
        if (maxHScroll() <= 0) return false;
        return MTRWidgets.isOver(mouseX, mouseY, hTrackLeft(), hScrollbarY(), hTrackWidth(), H_SCROLLBAR);
    }

    private boolean overSummaryVBar(double mouseX, double mouseY) {
        if (detailSummaryLines() <= detailSummaryRowCount()) return false;
        return MTRWidgets.isOver(mouseX, mouseY, detailX() + panelWidth - V_SCROLLBAR, detailSummaryTop(),
                V_SCROLLBAR, detailSummaryHeight());
    }

    private boolean overDetailVBar(double mouseX, double mouseY) {
        if (detailLineCount() <= detailBodyRowCount()) return false;
        return MTRWidgets.isOver(mouseX, mouseY, detailX() + panelWidth - V_SCROLLBAR, detailBodyTop(),
                V_SCROLLBAR, detailBodyBottom() - detailBodyTop());
    }

    private boolean overDetailHBar(double mouseX, double mouseY) {
        if (maxDetailHScroll() <= 0) return false;
        return MTRWidgets.isOver(mouseX, mouseY, detailHTrackLeft(), detailHScrollbarY(),
                detailHTrackWidth(), H_SCROLLBAR);
    }

    /**
     * Maps a pointer position on a track to a scroll offset, treating the grab point as
     * the middle of the thumb so the thumb lands under the cursor rather than jumping.
     */
    private static int scrollFromTrack(double pointer, int trackStart, int trackLength, int thumbLength, int maxScroll) {
        int travel = trackLength - thumbLength;
        if (travel <= 0) return 0;

        double offset = pointer - trackStart - thumbLength / 2.0;
        return Math.clamp(Math.round(offset / travel * maxScroll), 0, maxScroll);
    }

    private void applyTimelineVDrag(double mouseY) {
        int rowsOnScreen = visibleRowCount();
        int maxScroll = Math.max(0, visible.size() - rowsOnScreen);
        int trackHeight = rowsBottom() - LIST_TOP;
        scroll = scrollFromTrack(mouseY, LIST_TOP, trackHeight,
                verticalThumbHeight(trackHeight, rowsOnScreen, visible.size()), maxScroll);
    }

    private void applyTimelineHDrag(double mouseX) {
        int trackWidth = hTrackWidth();
        hScroll = scrollFromTrack(mouseX, hTrackLeft(), trackWidth,
                horizontalThumbWidth(trackWidth), maxHScroll());
    }

    private void applyDetailVDrag(double mouseY) {
        int rowsOnScreen = detailBodyRowCount();
        int lineCount = detailLineCount();
        int maxScroll = Math.max(0, lineCount - rowsOnScreen);
        int top = detailBodyTop();
        int trackHeight = detailBodyBottom() - top;
        detailScroll = scrollFromTrack(mouseY, top, trackHeight,
                verticalThumbHeight(trackHeight, rowsOnScreen, lineCount), maxScroll);
    }

    private void applySummaryVDrag(double mouseY) {
        int rowsOnScreen = detailSummaryRowCount();
        int lineCount = detailSummaryLines();
        int trackHeight = detailSummaryHeight();
        summaryScroll = scrollFromTrack(mouseY, detailSummaryTop(), trackHeight,
                verticalThumbHeight(trackHeight, rowsOnScreen, lineCount),
                Math.max(0, lineCount - rowsOnScreen));
    }

    private void applyDetailHDrag(double mouseX) {
        int trackWidth = detailHTrackWidth();
        int thumbWidth = Math.clamp((long) trackWidth * trackWidth / Math.max(1, detailContentWidth()),
                16, trackWidth);
        detailHScroll = scrollFromTrack(mouseX, detailHTrackLeft(), trackWidth, thumbWidth, maxDetailHScroll());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        switch (dragging) {
            case TIMELINE_V -> applyTimelineVDrag(event.y());
            case TIMELINE_H -> applyTimelineHDrag(event.x());
            case DETAIL_V -> applyDetailVDrag(event.y());
            case DETAIL_H -> applyDetailHDrag(event.x());
            case SUMMARY_V -> applySummaryVDrag(event.y());
            case NONE -> {
                return super.mouseDragged(event, dragX, dragY);
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != Drag.NONE) {
            dragging = Drag.NONE;
            return true;
        }
        return super.mouseReleased(event);
    }
}
