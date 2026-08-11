package ml.mypals.microtimingreplay.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sounds.SoundEvents;

@Environment(EnvType.CLIENT)
public class MTRWidgets {

    public static final int PANEL_BG = 0xD0101014;
    public static final int PANEL_BORDER = 0xFF3A3A44;

    public static final int CHROME_BG = 0xE0202028;
    public static final int SCROLL_TRACK = 0x40FFFFFF;
    public static final int SCROLL_THUMB = 0xFF8090A8;
    public static final int SCROLL_THUMB_ACTIVE = 0xFFB0C4E0;
    public static final int CARD_BG = 0xFF1E1E26;
    public static final int CARD_BG_HOVER = 0xFF3C4A66;
    public static final int CARD_BG_ACTIVE = 0xFF2B6CB0;
    public static final int CARD_BORDER = 0xFF4A4A58;

    public static final int TEXT = 0xFFE8E8F0;
    public static final int TEXT_DIM = 0xFF9090A0;
    public static final int TEXT_ACCENT = 0xFF6ED0E0;
    public static final int TEXT_ON = 0xFF7BD88F;
    public static final int TEXT_OFF = 0xFFE06C75;

    public static final int CURSOR_ROW_BG = 0x60FFD24A;

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                             int background, int border) {
        graphics.fill(x, y, x + width, y + height, background);
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, border);
        graphics.fill(x, y, x + 1, y + height, border);
        graphics.fill(x + width - 1, y, x + width, y + height, border);
    }

    public static void card(GuiGraphicsExtractor graphics, Font font, Component label,
                            int x, int y, int width, int height, boolean hovered, boolean active) {
        int background = active ? CARD_BG_ACTIVE : hovered ? CARD_BG_HOVER : CARD_BG;
        panel(graphics, x, y, width, height, background, CARD_BORDER);
        graphics.centeredText(font, label, x + width / 2, y + (height - font.lineHeight) / 2 + 1, TEXT);
    }

    public static boolean isOver(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
    public static void playButtonClickSound(final SoundManager soundManager) {
        soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
    public static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }
}
