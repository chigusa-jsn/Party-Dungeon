package com.chigusa.partydungeon.client;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** 「読み込み」：パソコンにある書き出しファイルを選ぶ画面 */
public class BundleFilesScreen extends PDScreen {
    private static final int PER_PAGE = 6;
    private final Screen parent;
    private final List<BundleClient.FileInfo> files;
    private int page;

    public BundleFilesScreen(Screen parent) {
        super("読み込むファイルを選ぶ");
        this.parent = parent;
        this.files = BundleClient.list();
    }

    @Override
    protected void init() {
        clearWidgets();
        int w = Math.min(320, width - 20);
        int x = (width - w) / 2;
        int y = 50;
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy/MM/dd HH:mm");
        for (int i = page * PER_PAGE; i < Math.min(files.size(), (page + 1) * PER_PAGE); i++) {
            BundleClient.FileInfo f = files.get(i);
            String label = fmt.format(new Date(f.created())) + "  " + f.source() + "  （ダンジョン " + f.dungeons()
                    + "・依頼 " + f.quests() + "）";
            button(label, x, y, w, 20, b -> {
                BundleClient.upload(f.path());
                onClose();
            });
            y += 24;
        }
        int by = 50 + PER_PAGE * 24 + 6;
        if (page > 0) {
            button("◀ 前", x, by, 60, 20, b -> {
                page--;
                init();
            });
        }
        if ((page + 1) * PER_PAGE < files.size()) {
            button("次 ▶", x + w - 60, by, 60, 20, b -> {
                page++;
                init();
            });
        }
        button("フォルダを開く", x, by + 26, (w - 4) / 2, 20, b -> {
            try {
                java.nio.file.Files.createDirectories(BundleClient.dir());
            } catch (java.io.IOException ignored) {
                // 開けなくても続ける
            }
            Util.getPlatform().openFile(BundleClient.dir().toFile());
        });
        button("戻る", x + (w - 4) / 2 + 4, by + 26, (w - 4) / 2, 20, b -> minecraft.setScreen(parent));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawCenteredString(font, title, width / 2, 18, GOLD);
        g.drawCenteredString(font, files.isEmpty() ? "書き出しファイルがありません。先に「全部書き出し」をしてください。"
                : "選ぶとサーバーへ送られ、次の画面で読み込む物を選べます", width / 2, 32, GRAY);
    }
}
