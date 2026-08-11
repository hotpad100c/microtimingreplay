package ml.mypals.microtimingreplay.network;

import ml.mypals.microtimingreplay.replay.ReplaySession;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a session's flattened action list into the rows the client draws.
 *
 * <p>EXIT actions are dropped — they are bookkeeping, not user-facing — but they are
 * what tells us the real nesting depth, which the client needs to fold by level. The
 * scoreboard's phase/queue/update triple caps out at three levels; this does not.
 */
public class TimelineSnapshot {

    private static final int DEFAULT_COLOR = 0xFFFFFF;

    public record Result(List<MTRPayloads.TimelineRow> rows, int cursorRow) {}

    public static Result build(ReplaySession session) {
        List<ReplaySession.ReplayAction> flat = session.getFlatActionsSnapshot();
        int cursor = session.getActionCursor();

        List<MTRPayloads.TimelineRow> rows = new ArrayList<>(flat.size());
        int depth = 0;
        int cursorRow = -1;

        for (int i = 0; i < flat.size(); i++) {
            ReplaySession.ReplayAction action = flat.get(i);
            boolean isCursor = i == cursor - 1;

            if (action.type() == ReplaySession.ActionType.EXIT) {
                depth = Math.max(0, depth - 1);
                // Sitting on a scope's EXIT reads as "still inside that scope" to a user,
                // so point at the last row we emitted rather than nothing.
                if (isCursor) cursorRow = rows.size() - 1;
                continue;
            }

            if (isCursor) cursorRow = rows.size();

            rows.add(new MTRPayloads.TimelineRow(
                    action.visibleStepIndex(),
                    action.frame().getTick(),
                    depth,
                    (byte) (action.type() == ReplaySession.ActionType.ENTER ? 0 : 1),
                    colorOf(action),
                    action.event().getScoreboardText()));

            if (action.type() == ReplaySession.ActionType.ENTER) depth++;
        }

        return new Result(rows, cursorRow);
    }

    private static int colorOf(ReplaySession.ReplayAction action) {
        ChatFormatting format = action.event().getColor();
        if (format == null) return DEFAULT_COLOR;
        Integer rgb = format.getColor();
        return rgb == null ? DEFAULT_COLOR : rgb;
    }
}
