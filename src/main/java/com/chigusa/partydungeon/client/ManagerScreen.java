package com.chigusa.partydungeon.client;

import java.util.ArrayList;
import java.util.List;

import com.chigusa.partydungeon.net.PDNetwork;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** 製作メニュー：ダンジョンの一覧と、選んだダンジョンの操作 */
public class ManagerScreen extends PDScreen {
    private static final int ROW = 18;
    private static final String[] DIFFS = {"easy", "normal", "hard", "very_hard", "chaos"};
    private static final String[] DIFF_LABELS = {"イージー", "ノーマル", "ハード", "ベリーハード", "カオス"};

    private final List<CompoundTag> dungeons = new ArrayList<>();
    private final boolean inWorkshop;
    private String selected;
    private int diffIndex = 1;
    private boolean confirmDelete;
    private double scroll;

    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int px;
    private EditBox nameBox;

    public ManagerScreen(CompoundTag data) {
        super("製作メニュー");
        ListTag list = data.getList("dungeons", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            dungeons.add(list.getCompound(i));
        }
        this.inWorkshop = data.getBoolean("inWorkshop");
        this.selected = data.getString("selected");
        if (selected.isEmpty() && !dungeons.isEmpty()) {
            selected = dungeons.get(0).getString("id");
        }
    }

    private CompoundTag current() {
        for (CompoundTag d : dungeons) {
            if (d.getString("id").equals(selected)) {
                return d;
            }
        }
        return null;
    }

    private void act(String action) {
        CompoundTag t = new CompoundTag();
        t.putString("id", selected == null ? "" : selected);
        t.putString("difficulty", DIFFS[diffIndex]);
        if (nameBox != null) {
            t.putString("name", nameBox.getValue());
        }
        PDNetwork.sendAction("manager", action, t);
    }

    @Override
    protected void init() {
        clearWidgets();
        int total = Math.min(420, width - 20);
        int x0 = (width - total) / 2;
        listX = x0;
        listY = 50;
        listW = 150;
        listH = Math.max(60, height - listY - 40);
        px = x0 + listW + 10;
        int pw = total - listW - 10;

        // 上の段
        int bw = (total - 8) / 3;
        button("設計スペースへ", x0, 24, bw, 20, b -> {
            act("lobby");
            onClose();
        });
        button("空の区画を作る", x0 + bw + 4, 24, bw, 20, b -> minecraft.setScreen(new CreateScreen(this)));
        if (inWorkshop) {
            button("遊ぶモードに戻る", x0 + (bw + 4) * 2, 24, bw, 20, b -> {
                act("exit");
                onClose();
            });
        } else {
            button("製作モードへ", x0 + (bw + 4) * 2, 24, bw, 20, b -> act("enter"));
        }

        CompoundTag d = current();
        if (d == null) {
            return;
        }
        int y = listY;
        nameBox = textBox(px, y, pw - 64, d.getString("name"));
        button("名前を保存", px + pw - 60, y, 60, 18, b -> act("rename"));
        y += 70;
        int half = (pw - 4) / 2;
        button("区画へ移動", px, y, half, 20, b -> {
            act("goto");
            onClose();
        });
        button("報酬を設定", px + half + 4, y, half, 20, b -> act("rewards"));
        y += 24;
        button("クリア条件: " + d.getString("clear"), px, y, pw, 20, b -> act("clear"));
        y += 24;
        button(d.getBoolean("published") ? "もう一度公開（更新）" : "公開する", px, y, half, 20, b -> act("publish"));
        if (d.getBoolean("published")) {
            button("非公開にする", px + half + 4, y, half, 20, b -> act("unpublish"));
        }
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("難易度: " + DIFF_LABELS[diffIndex]), b -> {
            diffIndex = (diffIndex + 1) % DIFFS.length;
            b.setMessage(Component.literal("難易度: " + DIFF_LABELS[diffIndex]));
        }).bounds(px, y, half, 20).build());
        button("公開してテストプレイ", px + half + 4, y, half, 20, b -> {
            act("testplay");
            onClose();
        });
        y += 34;
        button(confirmDelete ? "本当に削除する？（もう一度押す）" : "削除", px, y, pw, 20, b -> {
            if (confirmDelete) {
                act("delete");
            } else {
                confirmDelete = true;
                init();
            }
        });
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            scroll = Math.max(0, Math.min(Math.max(0, dungeons.size() * ROW - listH), scroll - sy * ROW * 2));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int i = (int) ((my - listY + scroll) / ROW);
            if (i >= 0 && i < dungeons.size()) {
                selected = dungeons.get(i).getString("id");
                confirmDelete = false;
                init();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawCenteredString(font, title, width / 2, 8, GOLD);
        panel(g, listX, listY, listW, listH);
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < dungeons.size(); i++) {
            int y = listY + i * ROW - (int) scroll;
            CompoundTag d = dungeons.get(i);
            boolean sel = d.getString("id").equals(selected);
            if (sel) {
                g.fill(listX, y, listX + listW, y + ROW, 0xFF2E5E2E);
            }
            String name = d.getString("name");
            text(g, (d.getBoolean("published") ? "● " : "○ ") + font.plainSubstrByWidth(name, listW - 20), listX + 4,
                    y + 5, d.getBoolean("published") ? GOLD : GRAY);
        }
        g.disableScissor();
        if (dungeons.isEmpty()) {
            text(g, "まだありません", listX + 6, listY + 6, GRAY);
        }
        text(g, "● 公開中  ○ 未公開", listX, listY + listH + 4, GRAY);

        CompoundTag d = current();
        if (d == null) {
            text(g, "← 左の一覧から選ぶか、", px, listY + 6, GRAY);
            text(g, "「設計スペースへ」で間取りを描いて、", px, listY + 18, GRAY);
            text(g, "間取りの杖で右クリックしてください。", px, listY + 30, GRAY);
            return;
        }
        int y = listY + 24;
        text(g, d.getBoolean("published") ? "公開中（第" + d.getInt("revision") + "版）" : "未公開", px, y,
                d.getBoolean("published") ? GREEN : GRAY);
        text(g, "広さ " + d.getInt("size") + "×" + d.getInt("size") + "  高さ " + d.getInt("height"), px, y + 12, WHITE);
        text(g, "敵 " + d.getInt("enemies") + " か所（公開時点）  報酬 " + d.getInt("rewards") + " 個", px, y + 24, WHITE);
    }

    /** 何もない四角い区画を作る画面 */
    static class CreateScreen extends PDScreen {
        private final ManagerScreen parent;
        private EditBox name;
        private EditBox size;
        private EditBox heightBox;

        CreateScreen(ManagerScreen parent) {
            super("空の区画を作る");
            this.parent = parent;
        }

        @Override
        protected void init() {
            int x = width / 2 - 100;
            int y = height / 2 - 50;
            name = textBox(x + 80, y, 120, "");
            size = numberBox(x + 80, y + 26, 40, 48, null);
            heightBox = numberBox(x + 80, y + 52, 40, 24, null);
            button("作る", x, y + 84, 96, 20, b -> {
                CompoundTag t = new CompoundTag();
                t.putString("name", name.getValue());
                t.putInt("size", parse(size, 48, 16, 240));
                t.putInt("height", parse(heightBox, 24, 8, 96));
                PDNetwork.sendAction("manager", "create", t);
                onClose();
            });
            button("戻る", x + 104, y + 84, 96, 20, b -> minecraft.setScreen(parent));
            setInitialFocus(name);
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float partial) {
            super.render(g, mx, my, partial);
            int x = width / 2 - 100;
            int y = height / 2 - 50;
            g.drawCenteredString(font, title, width / 2, y - 24, GOLD);
            text(g, "名前", x, y + 5, WHITE);
            text(g, "広さ", x, y + 31, WHITE);
            text(g, "16〜240", x + 126, y + 31, GRAY);
            text(g, "高さ", x, y + 57, WHITE);
            text(g, "8〜96", x + 126, y + 57, GRAY);
        }
    }
}
