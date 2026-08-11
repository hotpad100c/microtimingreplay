package ml.mypals.microtimingreplay.client;

import ml.mypals.microtimingreplay.network.MTRPayloads;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * The client's mirror of what the server told us. Nothing here is authoritative — the
 * screens draw from it and send intents back, and the server's next push overwrites it.
 */
@Environment(EnvType.CLIENT)
public class ClientReplayState {

    private static boolean serverHasMod = false;
    private static boolean allowed = false;

    private static List<String> runningSessions = List.of();
    private static String subscribed = "";
    private static boolean cameraFollow = false;

    private static String timelineProfile = "";
    private static List<MTRPayloads.TimelineRow> rows = List.of();
    private static int cursorRow = -1;
    private static int totalRows = 0;
    private static long tick = 0;
    private static long totalTick = 0;

    private static final Map<Integer, MTRPayloads.DetailsS2C> DETAILS = new HashMap<>();
    private static List<MTRPayloads.FilterRow> filterRows = List.of();

    /** Bumped whenever {@link #rows} is replaced, so open screens know to rebuild. */
    private static int timelineRevision = 0;

    public static void reset() {
        serverHasMod = false;
        allowed = false;
        runningSessions = List.of();
        subscribed = "";
        cameraFollow = false;
        clearTimeline();
        filterRows = List.of();
    }

    private static void clearTimeline() {
        timelineProfile = "";
        rows = List.of();
        cursorRow = -1;
        totalRows = 0;
        tick = 0;
        totalTick = 0;
        DETAILS.clear();
        timelineRevision++;
    }

    // ── server handshake ─────────────────────────────────────────────────────

    public static void onHello(boolean serverSpeaksProtocol, boolean playerAllowed) {
        serverHasMod = serverSpeaksProtocol;
        allowed = playerAllowed;
    }

    /** False against a vanilla server, or one running a mismatched protocol. */
    public static boolean serverHasMod() {
        return serverHasMod;
    }

    /** Whether this player passes the server's permission check; purely for display. */
    public static boolean allowed() {
        return allowed;
    }

    public static void setCameraFollow(boolean enabled) {
        cameraFollow = enabled;
    }
    // ── sessions ─────────────────────────────────────────────────────────────

    public static void onSessions(List<String> running, String subscribedProfile, boolean following) {
        runningSessions = List.copyOf(running);
        boolean changed = !subscribed.equals(subscribedProfile);
        subscribed = subscribedProfile;
        cameraFollow = following;
        if (changed && subscribedProfile.isEmpty()) {
            clearTimeline();
        }
    }

    public static boolean cameraFollow() {
        return cameraFollow;
    }

    public static List<String> runningSessions() {
        return runningSessions;
    }

    public static String subscribed() {
        return subscribed;
    }

    public static boolean isWatching() {
        return !subscribed.isEmpty();
    }

    // ── timeline ─────────────────────────────────────────────────────────────

    public static void onTimeline(MTRPayloads.TimelineS2C payload) {
        timelineProfile = payload.profile();
        rows = payload.rows();
        cursorRow = payload.cursorRow();
        totalRows = rows.size();
        DETAILS.clear();
        timelineRevision++;
    }

    public static void onCursor(MTRPayloads.CursorS2C payload) {
        // A cursor for some other session means our timeline is stale; leave it alone
        // rather than pointing the highlight at an unrelated row.
        if (!payload.profile().equals(timelineProfile)) return;
        cursorRow = payload.cursorRow();
        tick = payload.tick();
        totalTick = payload.totalTick();
        totalRows = payload.totalRows();
    }

    public static List<MTRPayloads.TimelineRow> rows() {
        return rows;
    }

    public static boolean hasTimeline() {
        return !rows.isEmpty();
    }

    public static int cursorRow() {
        return cursorRow;
    }

    public static int totalRows() {
        return totalRows;
    }

    public static long tick() {
        return tick;
    }

    public static long totalTick() {
        return totalTick;
    }

    public static int timelineRevision() {
        return timelineRevision;
    }

    /** The step index shown on the row the cursor sits on, or -1. */
    public static int currentStep() {
        if (cursorRow < 0 || cursorRow >= rows.size()) return -1;
        return rows.get(cursorRow).step();
    }

    // ── on-demand details ────────────────────────────────────────────────────

    public static void onDetails(MTRPayloads.DetailsS2C payload) {
        DETAILS.put(payload.step(), payload);
    }

    /** Null until the server answers a {@code RequestDetailsC2S} for this step. */
    public static MTRPayloads.DetailsS2C details(int step) {
        return DETAILS.get(step);
    }

    // ── recording filter ─────────────────────────────────────────────────────

    public static void onFilter(List<MTRPayloads.FilterRow> incoming) {
        filterRows = List.copyOf(incoming);
    }

    public static List<MTRPayloads.FilterRow> filterRows() {
        return filterRows;
    }

    /** Categories in registration order — the tab strip of the filter screen. */
    public static List<String> filterCategories() {
        List<String> categories = new ArrayList<>();
        for (MTRPayloads.FilterRow row : filterRows) {
            if (!categories.contains(row.category())) categories.add(row.category());
        }
        return categories;
    }
}
