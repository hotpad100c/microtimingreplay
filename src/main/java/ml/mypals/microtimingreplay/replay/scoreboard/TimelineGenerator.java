package ml.mypals.microtimingreplay.replay.scoreboard;

import ml.mypals.microtimingreplay.event.MTREvent;
import ml.mypals.microtimingreplay.profile.TickFrame;
import ml.mypals.microtimingreplay.replay.ReplaySession.ReplayAction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TimelineGenerator {

    private record Node(MTREvent event, String prefix, boolean isCurrent, MutableComponent highlightTag,
                        int visibleStepIndex) {
    }

    public record ScoreLine(Component text, int stepIndex) {
    }

    public static List<ScoreLine> generateTimeline(TickFrame frame, ReplayAction currentAction, Map<MTREvent, Integer> stepMap) {
        List<Node> flattenedTree = new ArrayList<>();

        if (frame != null) {
            for (int i = 0; i < frame.getEvents().size(); i++) {
                traverse(frame.getEvents().get(i), "", i == frame.getEvents().size() - 1, flattenedTree, currentAction, stepMap);
            }
        }

        int currentIndex = -1;
        for (int i = 0; i < flattenedTree.size(); i++) {
            if (flattenedTree.get(i).isCurrent) {
                currentIndex = i;
                break;
            }
        }

        int windowSize = 15;
        int start = 0;
        int end = flattenedTree.size();

        if (flattenedTree.size() > windowSize) {
            if (currentIndex == -1) {
                end = windowSize;
            } else {
                start = Math.max(0, currentIndex - 7);
                end = Math.min(flattenedTree.size(), start + windowSize);
                if (end - start < windowSize) {
                    start = Math.max(0, end - windowSize);
                }
            }
        }

        List<ScoreLine> displayLines = new ArrayList<>();
        if (start > 0) {
            displayLines.add(new ScoreLine(Component.literal(".....").withStyle(ChatFormatting.GRAY), -1));
        }

        for (int i = start; i < end; i++) {
            Node node = flattenedTree.get(i);
            displayLines.add(new ScoreLine(formatNode(node), node.visibleStepIndex));
        }

        if (end < flattenedTree.size()) {
            displayLines.add(new ScoreLine(Component.literal("..... more").withStyle(ChatFormatting.GRAY), -1));
        }

        return displayLines;
    }

    private static void traverse(MTREvent event, String prefix, boolean isLast, List<Node> out,
            ReplayAction currentAction, Map<MTREvent, Integer> stepMap) {
        boolean isCurrent = false;
        MutableComponent highlight = null;

        if (currentAction != null && currentAction.event() == event) {
            isCurrent = true;
            /*highlight = switch (currentAction.type()) {
                case ENTER -> MTRComponent.translatable("mtr.scoreboard.status.entering", "← entering");
                case LEAF -> MTRComponent.translatable("mtr.scoreboard.status.current", "← current");
                case EXIT -> MTRComponent.translatable("mtr.scoreboard.status.done", "← done");
            };*///Uhh..
            highlight = switch (currentAction.type()) {
                case ENTER -> Component.literal("◀");
                case LEAF -> Component.literal("←");
                case EXIT -> Component.literal("⌽");
            };
        }

        int stepIndex = stepMap.getOrDefault(event, -1);
        out.add(new Node(event, prefix + (isLast ? "└" : "├"), isCurrent, highlight, stepIndex));

        String childPrefix = prefix + (isLast ? "  " : "│");
        List<MTREvent> children = event.getChildren();
        for (int i = 0; i < children.size(); i++) {
            traverse(children.get(i), childPrefix, i == children.size() - 1, out, currentAction, stepMap);
        }
    }

    private static Component formatNode(Node node) {
        MutableComponent comp = Component.literal(node.prefix).withStyle(ChatFormatting.GRAY);

        MutableComponent eventComp = node.event.getScoreboardText().withStyle(node.event.getColor());

        if (node.isCurrent) {
            comp.append(eventComp.withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE));
            comp.append(Component.literal(" ").append(node.highlightTag).withStyle(ChatFormatting.WHITE));
        } else {
            comp.append(eventComp);
        }

        return comp.withStyle(style -> style.withClickEvent(
                new ClickEvent.RunCommand("/say hi")
        ));
    }
}