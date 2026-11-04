package com.chigusa.partydungeon.client;

import com.chigusa.partydungeon.net.PDNetwork;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;

/** ゴールブロックの設定：どのくらい近づいたらゴールか */
public class GoalScreen extends PDScreen {
    private final long pos;
    private final int initRadius;
    private EditBox radius;

    public GoalScreen(CompoundTag data) {
        super("ゴールブロック");
        this.pos = data.getLong("pos");
        this.initRadius = data.contains("radius") ? data.getInt("radius") : 2;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 30;
        radius = numberBox(x + 110, y, 40, initRadius, null);
        button("保存", x, y + 40, 96, 20, b -> {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", pos);
            t.putInt("radius", parse(radius, 2, 1, 32));
            PDNetwork.sendAction("goal", "save", t);
            onClose();
        });
        button("キャンセル", x + 104, y + 40, 96, 20, b -> onClose());
        setInitialFocus(radius);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        int x = width / 2 - 100;
        int y = height / 2 - 30;
        g.drawCenteredString(font, title, width / 2, y - 30, GOLD);
        text(g, "ゴールの範囲（1〜32）", x, y + 5, WHITE);
        text(g, "クリア条件が「ゴール地点に着く」のとき、", x, y + 66, GRAY);
        text(g, "このブロックからこの距離に入るとクリア", x, y + 78, GRAY);
    }
}
