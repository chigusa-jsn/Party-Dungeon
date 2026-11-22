package com.chigusa.partydungeon.client;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.chigusa.partydungeon.net.PDNetwork;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** 読み込む前の確認：1つずつ、読み込むかどうか選ぶ */
public class BundlePreviewScreen extends PDScreen {
    private static final int PER_PAGE = 8;

    private static final class Row {
        final boolean quest;
        final String uid;
        final String name;
        final String status;
        boolean on;

        Row(boolean quest, CompoundTag t) {
            this.quest = quest;
            this.uid = t.getString("uid");
            this.name = t.getString("name");
            this.status = t.getString("status");
            // 新しい物と、こちらで変えていない物は最初からチェック
            this.on = status.equals("new") || status.equals("update");
        }

        String statusLabel() {
            return switch (status) {
                case "new" -> "§a新しい";
                case "update" -> "§b更新";
                case "same" -> "§7同じ（変更なし）";
                default -> "§cこちらで変更あり！";
            };
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final String header;
    private final boolean questsMissing;
    private int page;

    public BundlePreviewScreen(CompoundTag data) {
        super("読み込む物を選ぶ");
        ListTag d = data.getList("dungeons", Tag.TAG_COMPOUND);
        for (int i = 0; i < d.size(); i++) {
            rows.add(new Row(false, d.getCompound(i)));
        }
        ListTag q = data.getList("quests", Tag.TAG_COMPOUND);
        for (int i = 0; i < q.size(); i++) {
            rows.add(new Row(true, q.getCompound(i)));
        }
        header = data.getString("source") + " で " + new SimpleDateFormat("yyyy/MM/dd HH:mm").format(new Date(data.getLong("created")))
                + " に書き出したファイル";
        questsMissing = data.getBoolean("questsMissing");
    }

    @Override
    protected void init() {
        clearWidgets();
        int w = Math.min(340, width - 20);
        int x = (width - w) / 2;
        int y = 56;
        for (int i = page * PER_PAGE; i < Math.min(rows.size(), (page + 1) * PER_PAGE); i++) {
            Row r = rows.get(i);
            button(label(r), x, y, w, 20, b -> {
                r.on = !r.on;
                b.setMessage(net.minecraft.network.chat.Component.literal(label(r)));
            });
            y += 22;
        }
        int by = 56 + PER_PAGE * 22 + 4;
        if (page > 0) {
            button("◀ 前", x, by, 60, 20, b -> {
                page--;
                init();
            });
        }
        if ((page + 1) * PER_PAGE < rows.size()) {
            button("次 ▶", x + w - 60, by, 60, 20, b -> {
                page++;
                init();
            });
        }
        int third = (w - 8) / 3;
        button("読み込む", x, by + 26, third, 20, b -> {
            CompoundTag t = new CompoundTag();
            ListTag ds = new ListTag();
            ListTag qs = new ListTag();
            for (Row r : rows) {
                if (r.on) {
                    (r.quest ? qs : ds).add(StringTag.valueOf(r.uid));
                }
            }
            t.put("dungeons", ds);
            t.put("quests", qs);
            PDNetwork.sendAction("bundle", "apply", t);
            onClose();
        });
        button("全部チェック", x + third + 4, by + 26, third, 20, b -> {
            for (Row r : rows) {
                r.on = true;
            }
            init();
        });
        button("やめる", x + (third + 4) * 2, by + 26, third, 20, b -> onClose());
    }

    private static String label(Row r) {
        return (r.on ? "☑ " : "☐ ") + (r.quest ? "依頼「" : "ダンジョン「") + r.name + "」  " + r.statusLabel();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawCenteredString(font, title, width / 2, 14, GOLD);
        g.drawCenteredString(font, header, width / 2, 28, GRAY);
        g.drawCenteredString(font, questsMissing ? "§cこのサーバーに jsn-Craft がないので、依頼は読み込めません"
                : "「こちらで変更あり」は、読み込むとこちらで直した分が上書きされます", width / 2, 40, GRAY);
    }
}
