package ml.mypals.microtimingreplay.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;


public final class MTRHelpText {

    private MTRHelpText() {
    }

    public record Line(String text, boolean heading) {
    }

    public static final String TIMELINE_KEY = "mtr.help.timeline_screen";
    public static final String TIMELINE_FALLBACK = """
            Viewport (needs CameraFollow on)
              Middle-drag - orbit around the focus
              Shift + middle-drag - pan
              Wheel - move in and out
              Double-click a block - make it the new centre

            Search
              Ctrl + F - open the search bar
              Enter / F4 - next match
              Shift + Enter / Shift + F4 - previous match
              Esc - close the search bar

            Timeline
              Wheel - scroll the column under the pointer
              Ctrl + wheel - scroll sideways
              Shift + wheel - step the replay
              Click [+] / [-] - fold or unfold a subtree
              Click #N - jump the replay to that step""";

    public static final String COMMANDS_KEY = "mtr.help.commands";
    public static final String COMMANDS_FALLBACK = """
            Profiles
              /mtr profile create <name>
              /mtr profile delete <name>
              /mtr profile info <name>
              /mtr profile migrate <name> - pull a pre-per-world profile in
              /mtr profile area add <name> <pos1> <pos2> [area]
              /mtr profile area remove <name> <area>
              /mtr profile area modify pos <name> <area> <pos1> <pos2>
              /mtr profile area modify rename <name> <area> <new>

            Recording
              /mtr record <name> <ticks>
              /mtr record stop
              /mtr record clear <name>

            Replay
              /mtr replay start <name>
              /mtr replay stop <name>
              /mtr replay list
              /mtr replay subscribe <name> - watch this one
              /mtr replay unsubscribe
              /mtr replay screen [page] - open the timeline
              /mtr replay forward <unit> [amount]
              /mtr replay backward <unit> [amount]
              /mtr replay jump <step>
              /mtr replay dump <step> - print the call stack
              /mtr replay auto <direction> <unit> <delay> <steps>
              /mtr replay auto stop
              units: ticks, phase, queue, updates, steps

            Recording filter
              /mtr filter screen [page]
              /mtr filter toggle <event> - cycle DontRecord / NotEmpty / Everything
              /mtr filter set <event> <mode> - mode: off, non_empty, all
              /mtr filter reset

            Other
              /mtr help""";

    public static List<Line> lines(String key, String fallback) {
        String resolved = MTRComponent.translatable(key, fallback).getString();
        List<Line> lines = new ArrayList<>();
        for (String raw : resolved.split("\n", -1)) {
            String text = raw.strip();
            if (text.isEmpty()) continue;
            boolean heading = !raw.startsWith(" ") && !raw.startsWith("\t") && !raw.startsWith("　");
            lines.add(new Line(text, heading));
        }
        return lines;
    }


    public static MutableComponent asComponent(String key, String fallback) {
        return MTRComponent.translatable(key, fallback);
    }
}
