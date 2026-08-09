package ml.mypals.microtimingreplay.replay;

/**
 * Which replay session the code running right now belongs to.
 * <p>
 * Several replays can run at once, and the state they own — markers, piston displays,
 * stand-in entities, stack traces — has to stay separate or one session's step would
 * clobber another's. Threading a session id through {@code MTREvent.applySelf} and
 * {@code display} would mean touching every event class, so the session is published
 * here instead and the managers read it.
 * <p>
 * A plain static is enough: stepping and rendering are synchronous on the server
 * thread, one session at a time. Nesting is supported so a session can be entered from
 * inside another's scope without losing the outer one.
 */
public final class ReplayContext {

    private ReplayContext() {}

    private static String current = null;

    /** The session that owns the current work, or {@code null} outside any replay. */
    public static String current() {
        return current;
    }

    /** Runs {@code body} attributed to {@code sessionId}. */
    public static void with(String sessionId, Runnable body) {
        String previous = current;
        current = sessionId;
        try {
            body.run();
        } finally {
            current = previous;
        }
    }

    /** {@link #with} for work that produces a value. */
    public static int callInt(String sessionId, java.util.function.IntSupplier body) {
        String previous = current;
        current = sessionId;
        try {
            return body.getAsInt();
        } finally {
            current = previous;
        }
    }

    /**
     * Key for per-session maps. Work that somehow escapes a session scope lands in a
     * shared bucket rather than corrupting a real session's state.
     */
    public static String key() {
        return current == null ? "" : current;
    }
}
