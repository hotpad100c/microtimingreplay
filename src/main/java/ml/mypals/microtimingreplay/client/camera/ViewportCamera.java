/*
 * The orbit / pan / dolly conventions in this file are adapted from
 * YuushyaModellingEnhancedEditor's CameraController:
 *
 *   https://github.com/zhongbai2333/YuushyaModellingEnhancedEditor
 *   Copyright (c) zhongbai2333 — MIT License
 */
package ml.mypals.microtimingreplay.client.camera;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class ViewportCamera {

    private static final double MIN_POLE_ANGLE = 1.5;
    private static final double MIN_DISTANCE = 1.0;
    private static final double MAX_DISTANCE = 256.0;
    private static final double DEGREES_PER_PIXEL = 0.35;
    private static final double EPSILON = 1.0e-6;

    private double yaw;
    private double pitch;
    private double distance = 8.0;

    private Vec3 focus = Vec3.ZERO;

    private boolean primed;

    public boolean isPrimed() {
        return primed;
    }

    public Vec3 focus() {
        return focus;
    }


    public void reset(Vec3 eye, Vec3 focus) {
        this.focus = focus;

        Vec3 offset = eye.subtract(focus);
        double length = offset.length();
        if (length < EPSILON) {
            this.distance = MIN_DISTANCE;
            this.yaw = 0.0;
            this.pitch = 0.0;
            this.primed = true;
            return;
        }

        this.distance = Math.clamp(length, MIN_DISTANCE, MAX_DISTANCE);
        double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        this.yaw = Math.toDegrees(Math.atan2(offset.x, -offset.z));
        this.pitch = Math.toDegrees(Math.atan2(offset.y, horizontal));
        clampPitch();
        this.primed = true;
    }

    public void retarget(Vec3 focus) {
        this.focus = focus;
    }


    public void orbit(double dragPixelsX, double dragPixelsY) {
        yaw = wrapDegrees(yaw + dragPixelsX * DEGREES_PER_PIXEL);
        pitch += dragPixelsY * DEGREES_PER_PIXEL;
        clampPitch();
    }


    public void pan(double dragPixelsX, double dragPixelsY, int viewportHeight, double fovDegrees) {
        if (viewportHeight <= 0) return;

        double worldPerPixel = 2.0 * distance * Math.tan(Math.toRadians(fovDegrees) * 0.5) / viewportHeight;

        Vec3 right = rightVector();
        focus = focus
                .add(right.scale(-dragPixelsX * worldPerPixel))
                .add(upVector(right).scale(dragPixelsY * worldPerPixel));
    }

    public Vec3 rightVector() {
        Vec3 forward = lookDirection();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        return right.lengthSqr() < EPSILON ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
    }

    private Vec3 upVector(Vec3 right) {
        return right.cross(lookDirection()).normalize();
    }

    /**
     * World-space ray through a screen pixel, for picking.
     *
     * <p>Undoes the game's own projection, so it wants the whole window — the caller's
     * viewport is only a hole punched in the GUI, the world behind it is drawn full-screen.
     */
    public Vec3 rayThrough(double mouseX, double mouseY, int screenWidth, int screenHeight, double fovDegrees) {
        int width = Math.max(1, screenWidth);
        int height = Math.max(1, screenHeight);

        double tanHalfFov = Math.tan(Math.toRadians(fovDegrees) * 0.5);
        double aspect = (double) width / height;
        double ndcX = 2.0 * mouseX / width - 1.0;
        double ndcY = 1.0 - 2.0 * mouseY / height;

        Vec3 right = rightVector();
        return lookDirection()
                .add(right.scale(ndcX * aspect * tanHalfFov))
                .add(upVector(right).scale(ndcY * tanHalfFov))
                .normalize();
    }

    public void dolly(double wheelSteps) {
        distance = Math.clamp(distance * Math.exp(-0.2 * wheelSteps), MIN_DISTANCE, MAX_DISTANCE);
    }

    public Vec3 lookDirection() {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double horizontal = Math.cos(pitchRad);
        return new Vec3(-Math.sin(yawRad) * horizontal, -Math.sin(pitchRad), Math.cos(yawRad) * horizontal);
    }

    public Vec3 eyePosition() {
        return focus.subtract(lookDirection().scale(distance));
    }

    public float yaw() {
        return (float) yaw;
    }

    public float pitch() {
        return (float) pitch;
    }

    private void clampPitch() {
        double limit = 90.0 - MIN_POLE_ANGLE;
        pitch = Math.clamp(pitch, -limit, limit);
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }
}
