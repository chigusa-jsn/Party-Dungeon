package com.chigusa.partydungeon.menu;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.chigusa.partydungeon.util.Msg;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.ServerChatEvent;

/**
 * 画面では文字が打てないので、「次にチャットに打った言葉」を受け取る仕組み。
 * 受け取った言葉はチャットには流れない。「キャンセル」と打つとやめられる。
 */
public final class ChatInput {
    private static final Map<UUID, Consumer<String>> WAITING = new HashMap<>();

    public static void ask(ServerPlayer p, String question, Consumer<String> then) {
        p.closeContainer();
        WAITING.put(p.getUUID(), then);
        Msg.info(p, question);
        Msg.info(p, "（チャットに入力してください。やめるときは「キャンセル」）");
    }

    public static void onChat(ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        Consumer<String> then = WAITING.remove(p.getUUID());
        if (then == null) {
            return;
        }
        e.setCanceled(true);
        String text = e.getRawText().trim();
        p.server.execute(() -> {
            if (text.isEmpty() || text.equals("キャンセル") || text.equalsIgnoreCase("cancel")) {
                Msg.info(p, "キャンセルしました。");
                return;
            }
            then.accept(text);
        });
    }

    public static void forget(UUID id) {
        WAITING.remove(id);
    }

    private ChatInput() {
    }
}
