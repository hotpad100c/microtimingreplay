package ml.mypals.microtimingreplay.replay.dialog;

import net.minecraft.server.dialog.body.DialogBody;

import ml.mypals.microtimingreplay.event.*;
import ml.mypals.microtimingreplay.replay.ReplayEngine;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.*;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TimelineScreenGenerator {

    private static final int ENTRIES_PER_PAGE = 40;
    private static final int BODY_WIDTH = 370;

    // ── Pixel-width table for Minecraft's default font ────────────────────
    // Source: the default bitmap font provider's
    // glyph widths for ASCII (glyph width + 1 shadow pixel).
    // For non-ASCII we fall back to 6.
    // Values are *rendered* widths (glyph + 1 for shadow).
    private static final int[] ASCII_WIDTHS = {
        // 32–127 (space through ~)
        4, 2, 6, 6, 6, 6, 6, 3, 5, 5, 6, 6, 3, 6, 2, 6,  // sp ! " # $ % & ' ( ) * + , - . /
        6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 2, 2, 6, 6, 6, 6,  // 0-9 : ; < = > ?
        6, 6, 6, 6, 6, 6, 6, 6, 6, 4, 6, 6, 6, 6, 6, 6,  // @ A-O
        6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 4, 6, 4, 6, 6,  // P-Z [ \ ] ^ _
        3, 6, 6, 6, 6, 6, 5, 6, 6, 2, 5, 5, 3, 6, 6, 6,  // ` a-o
        6, 6, 5, 6, 4, 6, 6, 6, 6, 6, 6, 5, 2, 5, 7, 6   // p-z { | } ~ DEL
    };

    private static int widthOf(String s) {
        int w = 0;
        for (char c : s.toCharArray()) {
            if (c >= 32 && c < 128) {
                w += ASCII_WIDTHS[c - 32];
            } else if (c == '\u2019' || c == '\u201C' || c == '\u201D') {
                w += 6;
            } else {
                w += 6;
            }
        }
        return w;
    }

    private static String pad(int missingPx) {
        if (missingPx <= 0) return "";
        int spaces     = missingPx / 4;
        int hairSpaces = missingPx % 4;
        return " ".repeat(spaces) + "\u200A".repeat(hairSpaces);
    }


    public static void openTimeline(ServerPlayer player, int page) {
        List<ReplayEngine.ReplayAction> flatActions = ReplayEngine.getFlatActionsSnapshot();
        if (flatActions.isEmpty()) {
            player.sendSystemMessage(
                    MTRComponent.translatable("commands.mtr.replay.not_playing", "Not currently playing a replay.").withStyle(ChatFormatting.RED));
            return;
        }

        // Collect ENTER + LEAF (skip EXIT – not user-facing)
        List<ReplayEngine.ReplayAction> visible = new ArrayList<>();
        for (ReplayEngine.ReplayAction a : flatActions) {
            if (a.type() != ReplayEngine.ActionType.EXIT) {
                visible.add(a);
            }
        }

        int totalPages = Math.max(1, (visible.size() + ENTRIES_PER_PAGE - 1) / ENTRIES_PER_PAGE);
        page = Math.clamp(page, 0, totalPages - 1);

        int from = page * ENTRIES_PER_PAGE;
        int to   = Math.min(from + ENTRIES_PER_PAGE, visible.size());
        List<ReplayEngine.ReplayAction> pageActions = visible.subList(from, to);

        record LineParts(String leftPlain, String rightPlain,
                         MutableComponent leftComp, MutableComponent rightComp) {}

        List<LineParts> parts = new ArrayList<>();
        int maxWidthPx = 0;

        int snapshotCursor = ReplayEngine.getActionCursor();

        long lastTick = Long.MIN_VALUE;

        for (ReplayEngine.ReplayAction action : pageActions) {
            MTREvent event      = action.event();
            boolean isEnter     = action.type() == ReplayEngine.ActionType.ENTER;
            int step            = action.visibleStepIndex();
            boolean isCurrent   = snapshotCursor > 0
                    && action == flatActions.get(snapshotCursor - 1);

            int indent = 0;
            if (action.phase()  != null) indent++;
            if (action.queue()  != null) indent++;
            if (action.update() != null) indent++;
            if (!isEnter)                indent++;
            String spaces = "  ".repeat(indent);

            ChatFormatting baseColor = event.getColor() != null ? event.getColor() : ChatFormatting.WHITE;
            String arrow  = isEnter ? "▶ " : "→ ";

            long tick     = action.frame().getTick();

            if (lastTick != tick) {
                String sepLeftPlain  = "[Tick" + tick + "] ";
                String sepRightPlain = "──────────────────────────────────────";
                parts.add(new LineParts(
                        sepLeftPlain, sepRightPlain,
                        Component.literal(sepLeftPlain).withStyle(ChatFormatting.DARK_GRAY),
                        Component.literal(sepRightPlain).withStyle(ChatFormatting.DARK_GRAY)));
                lastTick = tick;
            }

            HoverEvent.ShowText infoHover = new HoverEvent.ShowText(event.fillHoverText());

            MutableComponent leftComp = Component.literal("[Tick" + tick + "] ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(spaces + arrow).append(event.getScoreboardText().copy().withStyle(s -> s.withHoverEvent(infoHover)))
                            .withStyle(isCurrent
                                    ? Style.EMPTY.applyFormat(baseColor)
                                        .applyFormat(ChatFormatting.BOLD)
                                        .applyFormat(ChatFormatting.UNDERLINE)
                                    : Style.EMPTY.applyFormat(baseColor)));


            String leftPlain = leftComp.getString();
            String rightPlain = "[$] [#" + step + "↗]";

            Style stackTraceStyle = Style.EMPTY
                    .applyFormat(ChatFormatting.GOLD)
                    .applyFormat(ChatFormatting.UNDERLINE)
                    .withClickEvent(new ClickEvent.RunCommand("/mtr replay dump " + step))
                    .withHoverEvent(new HoverEvent.ShowText(
                            MTRComponent.translatable("mtr.timeline.hover_stacktrace", "Dump stacktrace for step #%d", step)
                                    .withStyle(ChatFormatting.GOLD)));

            Style jumpStyle = Style.EMPTY
                    .applyFormat(ChatFormatting.DARK_AQUA)
                    .applyFormat(ChatFormatting.UNDERLINE)
                    .withClickEvent(new ClickEvent.RunCommand("/mtr replay jump " + step))
                    .withHoverEvent(new HoverEvent.ShowText(
                            MTRComponent.translatable("mtr.timeline.hover_jump", "Jump to step #%d", step)
                                    .withStyle(ChatFormatting.AQUA)));

            MutableComponent rightComp = Component.literal("[$]").withStyle(stackTraceStyle)
                    .append(Component.literal(" [#" + step + "↗]").withStyle(jumpStyle));

            int lineWidth = widthOf(leftPlain) + widthOf(rightPlain);
            if (lineWidth > maxWidthPx) maxWidthPx = lineWidth;

            parts.add(new LineParts(leftPlain, rightPlain, leftComp, rightComp));
        }

        // Pad each line to maxWidthPx
        List<PlainMessage> bodyLines = new ArrayList<>();

        // Header
        bodyLines.add(plainMsg(
                MTRComponent.translatable("mtr.scoreboard.title", "Replay Timeline")
                        .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD),
                BODY_WIDTH));

        for (LineParts p : parts) {
            int lineWidth = widthOf(p.leftPlain()) + widthOf(p.rightPlain());
            int missing   = Math.max(0, maxWidthPx - lineWidth);

            MutableComponent full = p.leftComp()
                    .append(Component.literal(pad(missing)))
                    .append(p.rightComp());

            bodyLines.add(plainMsg(full, BODY_WIDTH));
        }

        List<ActionButton> navButtons = new ArrayList<>();
        if (page > 0) {
            navButtons.add(navBtn(
                    MTRComponent.translatable("mtr.timeline.btn_prev", "◀ Prev").withStyle(ChatFormatting.YELLOW),
                    "/mtr replay screen " + (page - 1), 150));
        }
        if (page < totalPages - 1) {
            navButtons.add(navBtn(
                    MTRComponent.translatable("mtr.timeline.btn_next", "Next ▶").withStyle(ChatFormatting.YELLOW),
                    "/mtr replay screen " + (page + 1), 150));
        }
        navButtons.add(new ActionButton(
                new CommonButtonData(MTRComponent.translatable("mtr.timeline.btn_close", "✕ Close").withStyle(ChatFormatting.GRAY),
                        Optional.empty(), 150),
                Optional.empty()));

        player.openDialog(Holder.direct(getMultiActionDialog(bodyLines,
                MTRComponent.translatable("mtr.timeline.dialog_title", "MTR Timeline").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                navButtons)));
    }

    private static @NonNull MultiActionDialog getMultiActionDialog(
            List<PlainMessage> bodyLines, Component title, List<ActionButton> navButtons) {

        List<DialogBody> dialogBodies = new ArrayList<>(bodyLines);

        return new MultiActionDialog(
                new CommonDialogData(
                        title,
                        Optional.empty(),
                        true,
                        false,
                        DialogAction.CLOSE,
                        dialogBodies,
                        List.of()
                ),
                navButtons,
                Optional.empty(),
                navButtons.size()
        );
    }


    private static PlainMessage plainMsg(Component text, int width) {
        return new PlainMessage(text, width);
    }

    private static ActionButton navBtn(Component label, String command, int width) {
        return new ActionButton(
                new CommonButtonData(label, Optional.empty(), width),
                Optional.of(new StaticAction(new ClickEvent.RunCommand(command))));
    }
}
