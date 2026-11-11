package com.chigusa.partydungeon.client;

import com.chigusa.partydungeon.net.PDNetwork;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;

/** 間取りから部屋を建てる画面 */
public class PlanScreen extends PDScreen {
    private final long pos;
    private final int w;
    private final int h;
    private final int cells;
    private final boolean hasStart;
    private final boolean hasBoss;
    private final int maxSize;

    private EditBox name;
    private EditBox scale;
    private EditBox corridor;
    private EditBox heightBox;

    public PlanScreen(CompoundTag data) {
        super("間取りから部屋を作る");
        this.pos = data.getLong("pos");
        this.w = data.getInt("w");
        this.h = data.getInt("h");
        this.cells = data.getInt("cells");
        this.hasStart = data.getBoolean("hasStart");
        this.hasBoss = data.getBoolean("hasBoss");
        this.maxSize = data.getInt("maxSize");
    }

    @Override
    protected void init() {
        int x = width / 2 - 130;
        int y = height / 2 - 70;
        name = textBox(x + 110, y, 150, name == null ? "" : name.getValue());
        scale = row(x, y + 26, 8, 3, 64);
        corridor = row(x, y + 52, 3, 1, 64);
        heightBox = row(x, y + 78, 7, 5, 160);
        button("作る", x, y + 130, 126, 20, b -> {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", pos);
            t.putString("name", name.getValue());
            t.putInt("scale", parse(scale, 8, 3, 64));
            t.putInt("corridor", parse(corridor, 3, 1, 64));
            t.putInt("height", parse(heightBox, 7, 5, 160));
            PDNetwork.sendAction("plan", "build", t);
            onClose();
        });
        button("キャンセル", x + 134, y + 130, 126, 20, b -> onClose());
        setInitialFocus(name);
    }

    /** 数字の入力欄と ＋／－ ボタン */
    private EditBox row(int x, int y, int value, int min, int max) {
        EditBox box = numberBox(x + 134, y, 40, value, null);
        button("－", x + 110, y, 20, 18, b -> box.setValue(Integer.toString(Math.max(min, parse(box, value, min, max) - 1))));
        button("＋", x + 178, y, 20, 18, b -> box.setValue(Integer.toString(Math.min(max, parse(box, value, min, max) + 1))));
        return box;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        int x = width / 2 - 130;
        int y = height / 2 - 70;
        g.drawCenteredString(font, title, width / 2, y - 34, GOLD);
        g.drawCenteredString(font, "間取り: " + w + " × " + h + " マス（" + cells + " マス）"
                + (hasStart ? "" : "  ※赤いスタートがありません"), width / 2, y - 18, hasStart ? GRAY : RED);
        text(g, "名前", x, y + 5, WHITE);
        text(g, "1マスの大きさ", x, y + 31, WHITE);
        text(g, "通路の幅", x, y + 57, WHITE);
        text(g, "高さ（床〜天井）", x, y + 83, WHITE);
        int s = parse(scale, 8, 3, 64);
        int size = Math.max(w, h) * s + 2;
        text(g, "→ 1マス = " + s + "×" + s + " ブロック", x + 204, y + 31, GRAY);
        text(g, "細い線の部分", x + 204, y + 57, GRAY);
        if (hasBoss) {
            text(g, "ボス部屋（紫）は円形・高さ 20 固定", x + 204, y + 83, 0xCC88FF);
        }
        boolean ok = size <= maxSize;
        text(g, "できあがりの広さ: 約 " + size + " × " + size + " ブロック" + (ok ? "" : "（大きすぎます。最大 " + maxSize + "）"),
                x, y + 106, ok ? GREEN : RED);
    }
}
