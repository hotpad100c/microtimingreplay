package ml.mypals.microtimingreplay.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * The one binding the add-on needs. Left alt is unbound in vanilla, which matters more
 * than usual here: the panel is a screen, so a stray trigger takes the mouse away from
 * the player. Rebind it in the controls screen if it collides with something else.
 */
@Environment(EnvType.CLIENT)
public class MTRKeys {

    /** 1.21.1 categories are plain translation keys; the lang files already carry this one. */
    public static final String CATEGORY = "key.category.microtimingreplay.main";

    public static KeyMapping panel;

    public static void register() {
        panel = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.microtimingreplay.panel",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT,
                CATEGORY));
    }

    /**
     * Whether the bound key is physically held right now.
     *
     * <p>{@link KeyMapping#isDown()} cannot answer this: opening a screen makes vanilla
     * release every mapping, and the panel <em>is</em> a screen. So we ask the window
     * directly, which keeps hold-to-show working while the panel is up.
     */
    public static boolean isPanelHeld() {
        if (panel == null) return false;

        InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(panel);
        Minecraft minecraft = Minecraft.getInstance();

        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(minecraft.getWindow().getWindow(), key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), key.getValue()) == GLFW.GLFW_PRESS;
            // Scan codes have no query API; fall back to the mapping's own state, which
            // is good enough to open the panel even if it cannot detect the release.
            default -> panel.isDown();
        };
    }
}
