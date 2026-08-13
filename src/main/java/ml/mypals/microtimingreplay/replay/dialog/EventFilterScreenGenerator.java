package ml.mypals.microtimingreplay.replay.dialog;

import ml.mypals.microtimingreplay.config.RecordMode;
import ml.mypals.microtimingreplay.config.RecordingEventRegistry;
import ml.mypals.microtimingreplay.config.RecordingFilterConfig;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EventFilterScreenGenerator {

    private static final int ITEMS_PER_PAGE = 8;
    private static final int BODY_WIDTH = 370;

    public static void openFilterScreen(ServerPlayer player, int page) {
        RecordingFilterConfig.ensureLoaded();
        List<RecordingEventRegistry.EventEntry> allEntries = new ArrayList<>(RecordingEventRegistry.getAll());
        int totalPages = Math.max(1, (allEntries.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        final int currentPage = Math.clamp(page, 0, totalPages - 1);

        int from = currentPage * ITEMS_PER_PAGE;
        int to = Math.min(from + ITEMS_PER_PAGE, allEntries.size());
        List<RecordingEventRegistry.EventEntry> pageEntries = allEntries.subList(from, to);

        List<PlainMessage> bodyLines = new ArrayList<>();
        bodyLines.add(new PlainMessage(
                MTRComponent.translatable("mtr.filter.subtitle", "Per-World Event Filter Config (event_filter.json)")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                BODY_WIDTH
        ));
        bodyLines.add(new PlainMessage(
                MTRComponent.translatable("mtr.filter.instruction", "Click any button below to toggle recording on/off for that event:")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                BODY_WIDTH
        ));
        bodyLines.add(new PlainMessage(
                MTRComponent.translatable("mtr.filter.page_info", "Page %d / %d  |  Total Rules: %d", currentPage + 1, totalPages, allEntries.size())
                        .withStyle(ChatFormatting.DARK_AQUA),
                BODY_WIDTH
        ));

        List<ActionButton> buttons = new ArrayList<>();

        for (RecordingEventRegistry.EventEntry entry : pageEntries) {
            RecordMode mode = RecordingFilterConfig.mode(entry.id());
            boolean enabled = mode.records();
            ChatFormatting statusColor = modeColor(mode);

            Component eventNameComp = MTRComponent.translatable("mtr.filter.event." + entry.id(), entry.defaultName());
            Component categoryComp = MTRComponent.translatable("mtr.filter.category." + entry.category().toLowerCase(), entry.category());

            MutableComponent label = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                    .append(modeLabel(entry, mode).copy().withStyle(statusColor, ChatFormatting.BOLD))
                    .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(eventNameComp.copy().withStyle(enabled ? ChatFormatting.WHITE : ChatFormatting.GRAY))
                    .append(Component.literal(" [").withStyle(ChatFormatting.DARK_GRAY))
                    .append(categoryComp.copy().withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));

            buttons.add(actionBtn(
                    label,
                    "/mtr filter toggle " + entry.id() + " " + currentPage,
                    300
            ));
        }

        if (currentPage > 0) {
            buttons.add(actionBtn(
                    MTRComponent.translatable("mtr.filter.btn_prev", "◀ Prev").withStyle(ChatFormatting.YELLOW),
                    "/mtr filter screen " + (currentPage - 1),
                    300
            ));
        }
        if (currentPage < totalPages - 1) {
            buttons.add(actionBtn(
                    MTRComponent.translatable("mtr.filter.btn_next", "Next ▶").withStyle(ChatFormatting.YELLOW),
                    "/mtr filter screen " + (currentPage + 1),
                    300
            ));
        }

        buttons.add(actionBtn(
                MTRComponent.translatable("mtr.filter.btn_reset", "Reset Defaults").withStyle(ChatFormatting.AQUA),
                "/mtr filter reset " + currentPage,
                300
        ));

        buttons.add(new ActionButton(
                new CommonButtonData(MTRComponent.translatable("mtr.filter.btn_close", "✕ Close").withStyle(ChatFormatting.GRAY), Optional.empty(), 300),
                Optional.empty()
        ));

        player.openDialog(Holder.direct(getMultiActionDialog(
                bodyLines,
                MTRComponent.translatable("mtr.filter.dialog_title", "Event Recording Filter").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                buttons
        )));
    }

    private static @NonNull MultiActionDialog getMultiActionDialog(
            List<PlainMessage> bodyLines, Component title, List<ActionButton> buttons) {
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
                buttons,
                Optional.empty(),
                1
        );
    }

    /** Options read as a plain switch; events name which of the three modes they are in. */
    private static Component modeLabel(RecordingEventRegistry.EventEntry entry, RecordMode mode) {
        if (entry.isOption()) {
            return mode.records()
                    ? MTRComponent.translatable("mtr.filter.mode.on", "On")
                    : MTRComponent.translatable("mtr.filter.mode.disabled", "Off");
        }
        return switch (mode) {
            case OFF -> MTRComponent.translatable("mtr.filter.mode.off", "DontRecord");
            case NON_EMPTY -> MTRComponent.translatable("mtr.filter.mode.non_empty", "NotEmpty");
            case ALL -> MTRComponent.translatable("mtr.filter.mode.all", "Everything");
        };
    }

    private static ChatFormatting modeColor(RecordMode mode) {
        return switch (mode) {
            case OFF -> ChatFormatting.RED;
            case NON_EMPTY -> ChatFormatting.YELLOW;
            case ALL -> ChatFormatting.GREEN;
        };
    }

    private static ActionButton actionBtn(Component label, String command, int width) {
        return new ActionButton(
                new CommonButtonData(label, Optional.empty(), width),
                Optional.of(new StaticAction(new ClickEvent.RunCommand(command))));
    }
}
