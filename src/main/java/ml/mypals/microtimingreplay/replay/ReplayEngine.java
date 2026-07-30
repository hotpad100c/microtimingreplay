package ml.mypals.microtimingreplay.replay;

import ml.mypals.microtimingreplay.event.MTREvent;
import ml.mypals.microtimingreplay.event.SetBlockEvent;
import ml.mypals.microtimingreplay.profile.MTRProfile;
import ml.mypals.microtimingreplay.profile.TickFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReplayEngine {
    private static MTRProfile currentProfile;
    private static long currentVirtualTick = 0;
    private static int frameCursor = 0;
    private static int stepCursor = 0;
    private static ServerBossEvent bossBar;
    private static Set<ServerPlayer> subscribers = new HashSet<>();

    public static void startReplay(MTRProfile profile, ServerLevel level) {
        currentProfile = profile;
        currentVirtualTick = 0;
        frameCursor = 0;
        stepCursor = 0;
        
        bossBar = new ServerBossEvent(java.util.UUID.randomUUID(), Component.literal("Replay: " + profile.getName()), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
        updateBossBar();
    }

    public static void stopReplay() {
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar = null;
        }
        subscribers.clear();
        currentProfile = null;
    }

    public static void subscribe(ServerPlayer player) {
        if (bossBar != null) {
            subscribers.add(player);
            bossBar.addPlayer(player);
        }
    }

    public static void unsubscribe(ServerPlayer player) {
        if (bossBar != null) {
            subscribers.remove(player);
            bossBar.removePlayer(player);
        }
    }

    private static void updateBossBar() {
        if (bossBar == null || currentProfile == null) return;
        
        List<TickFrame> frames = currentProfile.getFrames();
        long totalTick = frames.isEmpty() ? 0 : frames.get(frames.size() - 1).getTick();
        
        if (frames.isEmpty()) {
            bossBar.setName(Component.literal("Replay: No events"));
            bossBar.setProgress(1.0f);
            return;
        }

        int totalSteps = 0;
        int currentTotalSteps = 0;
        boolean isEmpty = false;

        for (int i = 0; i < frames.size(); i++) {
            TickFrame frame = frames.get(i);
            totalSteps += frame.getEvents().size();
            
            if (i < frameCursor) {
                currentTotalSteps += frame.getEvents().size();
            } else if (i == frameCursor) {
                currentTotalSteps += stepCursor;
            }
        }

        if (currentVirtualTick > 0 && currentVirtualTick <= totalTick) {
            isEmpty = true;
            for (TickFrame frame : frames) {
                if (frame.getTick() == currentVirtualTick) {
                    isEmpty = false;
                    break;
                }
            }
        }

        int stepsInCurrentFrame = frameCursor < frames.size() ? frames.get(frameCursor).getEvents().size() : 0;
        
        if (isEmpty) {
            bossBar.setColor(BossEvent.BossBarColor.RED);
            bossBar.setName(Component.literal(String.format("Replay [%s] Tick: %d / %d (empty) | Step: %d / %d", currentProfile.getName(), currentVirtualTick, totalTick, stepCursor, stepsInCurrentFrame)));
        } else {
            bossBar.setColor(BossEvent.BossBarColor.BLUE);
            bossBar.setName(Component.literal(String.format("Replay [%s] Tick: %d / %d | Step: %d / %d", currentProfile.getName(), currentVirtualTick, totalTick, stepCursor, stepsInCurrentFrame)));
        }
        
        if (totalSteps > 0) {
            bossBar.setProgress((float) currentTotalSteps / totalSteps);
        } else {
            bossBar.setProgress(1.0f);
        }
    }

    public static int stepForward(ServerLevel level, int steps) {
        if (currentProfile == null) return 0;
        List<TickFrame> frames = currentProfile.getFrames();
        int taken = 0;

        while (taken < steps && frameCursor < frames.size()) {
            TickFrame frame = frames.get(frameCursor);
            if (stepCursor == 0) {
                currentVirtualTick = frame.getTick();
            }

            MTREvent event = frame.getEvents().get(stepCursor);
            event.apply(level, true);
            stepCursor++;
            taken++;

            if (stepCursor >= frame.getEvents().size()) {
                frameCursor++;
                stepCursor = 0;
            }
        }
        
        updateBossBar();
        return taken;
    }

    public static int stepBackward(ServerLevel level, int steps) {
        if (currentProfile == null) return 0;
        List<TickFrame> frames = currentProfile.getFrames();
        int taken = 0;

        while (taken < steps) {
            if (stepCursor > 0) {
                stepCursor--;
                TickFrame frame = frames.get(frameCursor);
                frame.getEvents().get(stepCursor).apply(level, false);
                taken++;
                if (stepCursor == 0) {
                    currentVirtualTick = frameCursor > 0 ? frames.get(frameCursor - 1).getTick() : 0;
                } else {
                    currentVirtualTick = frame.getTick();
                }
            } else if (frameCursor > 0) {
                frameCursor--;
                TickFrame prevFrame = frames.get(frameCursor);
                stepCursor = prevFrame.getEvents().size() - 1;
                prevFrame.getEvents().get(stepCursor).apply(level, false);
                taken++;
                if (stepCursor == 0) {
                    currentVirtualTick = frameCursor > 0 ? frames.get(frameCursor - 1).getTick() : 0;
                } else {
                    currentVirtualTick = prevFrame.getTick();
                }
            } else {
                break;
            }
        }

        updateBossBar();
        return taken;
    }

    public static int tickForward(ServerLevel level, int ticks) {
        if (currentProfile == null) return 0;
        List<TickFrame> frames = currentProfile.getFrames();
        
        long targetTick = currentVirtualTick + ticks;
        
        while (frameCursor < frames.size()) {
            TickFrame frame = frames.get(frameCursor);
            if (frame.getTick() <= targetTick) {
                while (stepCursor < frame.getEvents().size()) {
                    frame.getEvents().get(stepCursor).apply(level, true);
                    stepCursor++;
                }
                frameCursor++;
                stepCursor = 0;
            } else {
                break;
            }
        }
        
        currentVirtualTick = targetTick;
        updateBossBar();
        return ticks;
    }

    public static int tickBackward(ServerLevel level, int ticks) {
        if (currentProfile == null) return 0;
        List<TickFrame> frames = currentProfile.getFrames();
        
        long targetTick = Math.max(0, currentVirtualTick - ticks);
        
        while (true) {
            if (stepCursor > 0) {
                TickFrame frame = frames.get(frameCursor);
                if (frame.getTick() > targetTick) {
                    while (stepCursor > 0) {
                        stepCursor--;
                        frame.getEvents().get(stepCursor).apply(level, false);
                    }
                } else {
                    break;
                }
            }
            
            if (frameCursor > 0) {
                TickFrame prevFrame = frames.get(frameCursor - 1);
                if (prevFrame.getTick() > targetTick) {
                    frameCursor--;
                    stepCursor = prevFrame.getEvents().size();
                    while (stepCursor > 0) {
                        stepCursor--;
                        prevFrame.getEvents().get(stepCursor).apply(level, false);
                    }
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        
        currentVirtualTick = targetTick;
        updateBossBar();
        return ticks;
    }
}
