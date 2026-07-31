package ml.mypals.microtimingreplay.replay.dialog;

import ml.mypals.microtimingreplay.replay.stackTrace.StackTraceManager;
import ml.mypals.microtimingreplay.util.MTRComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StackTraceScreenGenerator {

    private static final int BODY_WIDTH = 370;

    public static void openStackTrace(ServerPlayer player, int step, int page) {
        List<String> rawLines = StackTraceManager.get(step);
        if (rawLines == null || rawLines.isEmpty()) {
            player.sendSystemMessage(
                    MTRComponent.translatable("mtr.stacktrace.not_found", "No stack trace found for step #%d", step).withStyle(ChatFormatting.RED));
            return;
        }

        Input inputControl = getInput(rawLines);

        Component title = MTRComponent.translatable("mtr.stacktrace.dialog_title", "MTR StackTrace - Step #%d", step)
                .withStyle(ChatFormatting.GOLD);

        List<ActionButton> navButtons = new ArrayList<>();
        navButtons.add(navBtn(
                MTRComponent.translatable("mtr.stacktrace.btn_back", "↩ Back").withStyle(ChatFormatting.YELLOW),
                "/mtr replay screen", 120));
        navButtons.add(new ActionButton(
                new CommonButtonData(MTRComponent.translatable("mtr.stacktrace.btn_close", "✕ Close").withStyle(ChatFormatting.GRAY), Optional.empty(), 120),
                Optional.empty()));

        MutableComponent hoverText = MTRComponent.translatable("mtr.stacktrace.tooltip_title", "Step #%d StackTrace:\n", step)
                .withStyle(ChatFormatting.GOLD);

        int maxShow = Math.min(rawLines.size(), 20);
        for (int i = 0; i < maxShow; i++) {
            String line = rawLines.get(i);
            hoverText.append(Component.literal(String.format("#%02d ", i + 1)).withStyle(ChatFormatting.DARK_GRAY))
                     .append(formatStackTraceLine(line))
                     .append(Component.literal(i < maxShow - 1 ? "\n" : ""));
        }

        if (rawLines.size() > maxShow) {
            hoverText.append(Component.literal("\n").append(MTRComponent.translatable("mtr.stacktrace.tooltip_more", "... and %d more lines", rawLines.size() - maxShow)).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }

        Component headerComp = MTRComponent.translatable("mtr.stacktrace.header", "Step #%d StackTrace:", step)
                .withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent.CopyToClipboard(hoverText.getString()))
                        .withHoverEvent(new HoverEvent.ShowText(hoverText)));

        List<DialogBody> bodyList = List.of(
                new PlainMessage(headerComp, BODY_WIDTH));

        MultiActionDialog dialog = new MultiActionDialog(
                new CommonDialogData(
                        title,
                        Optional.empty(),
                        true,
                        false,
                        DialogAction.CLOSE,
                        bodyList,
                        List.of(inputControl)),
                navButtons,
                Optional.empty(),
                navButtons.size());

        player.openDialog(Holder.direct(dialog));
    }

    private static @NonNull Input getInput(List<String> rawLines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rawLines.size(); i++) {
            sb.append(rawLines.get(i));
            if (i < rawLines.size() - 1) {
                sb.append("\n➥ \n");
            }
        }
        String fullStackTraceText = sb.toString();

        TextInput textInput = new TextInput(
                350,
                MTRComponent.translatable("mtr.stacktrace.input_label", "StackTrace"),
                true,
                fullStackTraceText,
                200000,
                Optional.of(new TextInput.MultilineOptions(
                        Optional.empty(),
                        Optional.of(110))));

        return new Input("stacktrace", textInput);
    }

    private static @NonNull MutableComponent formatStackTraceLine(String rawLine) {
        String raw = rawLine;
        String module = "";
        int lastSlash = raw.lastIndexOf('/');
        if (lastSlash != -1) {
            module = raw.substring(0, lastSlash + 1);
            raw = raw.substring(lastSlash + 1);
        }

        int openParen = raw.indexOf('(');
        int closeParen = raw.lastIndexOf(')');
        String mainPart = (openParen != -1) ? raw.substring(0, openParen) : raw;
        String parenPart = (openParen != -1 && closeParen > openParen) ? raw.substring(openParen + 1, closeParen) : "";

        int lastDot = mainPart.lastIndexOf('.');
        String fullClassName = (lastDot != -1) ? mainPart.substring(0, lastDot) : mainPart;
        String methodName = (lastDot != -1) ? mainPart.substring(lastDot) : "";

        int classDot = fullClassName.lastIndexOf('.');
        String packageName = (classDot != -1) ? fullClassName.substring(0, classDot + 1) : "";
        String simpleClassName = (classDot != -1) ? fullClassName.substring(classDot + 1) : fullClassName;

        MutableComponent comp = Component.empty();

        if (!module.isEmpty()) {
            comp.append(Component.literal(module).withStyle(ChatFormatting.DARK_GRAY));
        }

        if (!packageName.isEmpty()) {
            comp.append(Component.literal(packageName).withStyle(ChatFormatting.GRAY));
        }

        comp.append(Component.literal(simpleClassName).withStyle(ChatFormatting.WHITE));

        if (!methodName.isEmpty()) {
            comp.append(Component.literal(methodName).withStyle(ChatFormatting.GOLD));
        }

        if (openParen != -1) {
            comp.append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY));
            if (!parenPart.isEmpty()) {
                int colon = parenPart.indexOf(':');
                if (colon != -1) {
                    String fileName = parenPart.substring(0, colon);
                    String lineNum = parenPart.substring(colon);
                    comp.append(Component.literal(fileName).withStyle(ChatFormatting.AQUA));
                    comp.append(Component.literal(lineNum).withStyle(ChatFormatting.LIGHT_PURPLE));
                } else {
                    comp.append(Component.literal(parenPart).withStyle(ChatFormatting.AQUA));
                }
            }
            comp.append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
        }

        return comp;
    }

    private static ActionButton navBtn(Component label, String command, int width) {
        return new ActionButton(
                new CommonButtonData(label, Optional.empty(), width),
                Optional.of(new StaticAction(new ClickEvent.RunCommand(command))));
    }
}
