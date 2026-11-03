package com.chigusa.partydungeon.client;

import org.lwjgl.glfw.GLFW;

import com.chigusa.partydungeon.net.OpenMenuPayload;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** クライアントだけの処理（P キーでメイン画面） */
public final class PDClient {
    public static final KeyMapping OPEN_MENU = new KeyMapping("key.partydungeon.menu",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, "key.categories.partydungeon");

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(OPEN_MENU));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            while (OPEN_MENU.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.getConnection() != null && mc.screen == null) {
                    PacketDistributor.sendToServer(OpenMenuPayload.INSTANCE);
                }
            }
        });
    }

    private PDClient() {
    }
}
