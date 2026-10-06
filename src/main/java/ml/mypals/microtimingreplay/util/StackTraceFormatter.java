package ml.mypals.microtimingreplay.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Colours a raw stack trace line for display.
 *
 * <p>This used to live in the server-dialog screen generator. 1.21.1 has no dialog API,
 * so the formatting moved here where both the chat fallback and the client timeline
 * screen can reach it.
 */
public final class StackTraceFormatter {

    private StackTraceFormatter() {
    }

    public static @NotNull MutableComponent formatStackTraceLine(String rawLine) {
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
}
