package ml.mypals.microtimingreplay.event;

import net.minecraft.nbt.StringTag;

import ml.mypals.microtimingreplay.config.RecordMode;
import ml.mypals.microtimingreplay.config.RecordingFilterConfig;
import ml.mypals.microtimingreplay.marker.MTRMarker;
import ml.mypals.microtimingreplay.util.DisplayUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class MTREvent {
    
    public interface EventFactory {
        MTREvent create(CompoundTag tag);
    }
    
    private static final Map<String, EventFactory> REGISTRY = new HashMap<>();
    
    public static void register(String type, EventFactory factory) {
        REGISTRY.put(type, factory);
    }
    private String type;
    private long tick;
    private final List<MTREvent> children = new ArrayList<>();
    private List<String> stackTrace;

    public MTREvent() {
        this.stackTrace = dumpStackTrace();
    }

    public MTREvent(String type, long tick) {
        this.type = type;
        this.tick = tick;
        this.stackTrace = dumpStackTrace();
    }

    public List<String> getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(List<String> stackTrace) {
        this.stackTrace = stackTrace;
    }

    public String getType() {
        return type;
    }
    public String getDimension() {
        return null;
    }
    public boolean hasDimension() {
        return getDimension() != null;
    }
    public ChatFormatting getColor() {
        return ChatFormatting.WHITE;
    }

    public boolean isQueueScope() {
        return false;
    }

    public MutableComponent fillHoverText() {
        return Component.empty();
    }

    public MutableComponent getScoreboardText() {
        return Component.literal(getType());
    }

    public long getTick() {
        return tick;
    }

    public List<MTREvent> getChildren() {
        return children;
    }

    public void addChild(MTREvent event) {
        children.add(event);
    }

    public void removeChild(MTREvent event) {
        children.remove(event);
    }
    public String filterId() {
        return null;
    }

    /**
     * Whether to keep this event when it ended up with no children.
     *
     * <p>Only reached for events that went through {@code pushEvent}; leaf events are recorded
     * outright and never come here, which is why picking {@link RecordMode#NON_EMPTY} for a
     * leaf behaves the same as {@link RecordMode#ALL}.
     */
    public boolean saveEvenWithoutAction(MinecraftServer server) {
        String id = filterId();
        return id == null || RecordingFilterConfig.mode(id) == RecordMode.ALL;
    }

    public static final float MARKER_SCALE = 1.005f;

    /**
     * The block this event's marker sits on, or {@code null} when the event has no
     * place in the world (phases, level ticks). Also tells the replay engine where
     * markers stack, so it can turn overlapping ones into pillars.
     */
    public BlockPos getMarkerPos() {
        return null;
    }

    /**
     * Draws this event's marker. The default is a glass cube tinted by
     * {@link #getColor()} at {@link #getMarkerPos()}.
     * <p>
     * Override and call {@code super} to add extra displays on top (see
     * {@link SetBlockEvent}, which appends the state diff as floating text).
     * Override with an empty body to opt out entirely — for events drawn by other
     * means, such as pistons (their own block displays) or entities (glow).
     *
     * @param scale cube scale; the engine stretches it into a pillar when several
     *              markers land on the same block
     */
    public void display(ServerLevel level, Vector3f scale) {
        BlockPos pos = getMarkerPos();
        if (pos == null) return;
        ChatFormatting color = getColor();
        MTRMarker.spawnBlockDisplay(level, Vec3.atLowerCornerOf(pos), DisplayUtils.getGlassState(color), scale, color);
    }

    /** Draws this event's marker at the default scale. */
    public void display(ServerLevel level) {
        display(level, new Vector3f(MARKER_SCALE, MARKER_SCALE, MARKER_SCALE));
    }

    /**
     * Applies only this event's own effect on the world, never its children.
     * <p>
     * This is what {@code ReplaySession} calls, because it walks every node of the
     * flattened tree individually — recursing here would apply descendants a
     * second time. Scope events that also mutate the world (notably
     * {@link SetBlockEvent}, which encloses the updates its own write triggers)
     * depend on this running for their {@code ENTER} action.
     *
     * @param forward {@code true} when stepping forward, {@code false} when rolling back
     */
    public void applySelf(ServerLevel level, boolean forward) {
    }

    /**
     * Applies this event together with its whole subtree, in replay order.
     * The step engine uses {@link #applySelf} instead, node by node.
     */
    @Deprecated
    public void apply(ServerLevel level, boolean forward) {
        applySelf(level, forward);
        if (forward) {
            for (MTREvent child : children) {
                child.apply(level, true);
            }
        } else {
            for (int i = children.size() - 1; i >= 0; i--) {
                children.get(i).apply(level, false);
            }
        }
    }

    public abstract CompoundTag writeNBT();

    public static MTREvent readNBT(CompoundTag tag) {
        String eventType = tag.getString("type").orElse("unknown");
        EventFactory factory = REGISTRY.get(eventType);
        if (factory != null) {
            return factory.create(tag);
        }
        
        return new MTREvent(eventType, tag.getLong("tick").orElse(0L)) {
            @Override
            public CompoundTag writeNBT() {
                CompoundTag fallback = new CompoundTag();
                fallback.putString("type", getType());
                fallback.putLong("tick", getTick());
                if (!getChildren().isEmpty()) {
                    ListTag childList = new ListTag();
                    for (MTREvent child : getChildren()) {
                        childList.add(child.writeNBT());
                    }
                    fallback.put("children", childList);
                }
                return fallback;
            }
        };
    }
    public static List<String> dumpStackTrace() {
        List<String> stacks = new ArrayList<>();
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTraceElements) {
            String className = element.getClassName();
            String methodName = element.getMethodName();

            if (className.equals("java.lang.Thread")) continue;
            if (className.startsWith("ml.mypals.microtimingreplay")) continue;
            if (methodName.contains("mtr$") || methodName.contains("$mtr$")) continue;

            stacks.add(element.toString());
        }
        return stacks;
    }
    
    public void writeStackTraceNBT(CompoundTag tag) {
        if (stackTrace != null && !stackTrace.isEmpty()) {
            ListTag linesTag = new ListTag();
            for (String line : stackTrace) {
                linesTag.add(StringTag.valueOf(line));
            }
            tag.put("stackTrace", linesTag);
        }
    }

    public static void readStackTraceNBT(MTREvent event, CompoundTag tag) {
        if (event != null && tag.contains("stackTrace")) {
            ListTag linesTag = tag.getList("stackTrace").orElse(new ListTag());
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < linesTag.size(); i++) {
                linesTag.getString(i).ifPresent(lines::add);
            }
            event.setStackTrace(lines);
        }
    }

    public static void readChildrenNBT(MTREvent event, CompoundTag tag) {
        readStackTraceNBT(event, tag);
        if (tag.contains("children")) {
            ListTag list = tag.getList("children").orElse(new ListTag());
            for (int i = 0; i < list.size(); i++) {
                event.addChild(readNBT(list.getCompound(i).orElse(new CompoundTag())));
            }
        }
    }
}
