package com.chigusa.partydungeon.client;

import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 専用画面の共通部品 */
abstract class PDScreen extends Screen {
    protected static final int WHITE = 0xFFFFFF;
    protected static final int GRAY = 0xA0A0A0;
    protected static final int GOLD = 0xFFAA00;
    protected static final int GREEN = 0x55FF55;
    protected static final int RED = 0xFF5555;

    protected PDScreen(String title) {
        super(Component.literal(title));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected Button button(String label, int x, int y, int w, int h, Button.OnPress onPress) {
        return addRenderableWidget(Button.builder(Component.literal(label), onPress).bounds(x, y, w, h).build());
    }

    /** 数字だけ入る入力欄 */
    protected EditBox numberBox(int x, int y, int w, int value, Consumer<String> onChange) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.empty());
        box.setMaxLength(4);
        box.setFilter(s -> s.isEmpty() || s.matches("\\d{0,4}"));
        box.setValue(Integer.toString(value));
        if (onChange != null) {
            box.setResponder(onChange);
        }
        return addRenderableWidget(box);
    }

    protected EditBox textBox(int x, int y, int w, String value) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.empty());
        box.setMaxLength(40);
        box.setValue(value);
        return addRenderableWidget(box);
    }

    protected static int parse(EditBox box, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(box.getValue().trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 背景の暗い板 */
    protected void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF6B6B6B);
        g.fill(x, y, x + w, y + h, 0xE0101010);
    }

    protected void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font, s, x, y, color);
    }
}
