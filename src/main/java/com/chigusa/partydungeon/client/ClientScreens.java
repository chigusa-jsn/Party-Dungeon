package com.chigusa.partydungeon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/** サーバーから「この画面を開いて」と言われたときに、クライアントで画面を開く */
public final class ClientScreens {
    public static void open(String screen, CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        switch (screen) {
            case "spawner" -> mc.setScreen(new SpawnerScreen(data));
            case "goal" -> mc.setScreen(new GoalScreen(data));
            case "plan" -> mc.setScreen(new PlanScreen(data));
            case "manager" -> mc.setScreen(new ManagerScreen(data));
            default -> {
            }
        }
    }

    private ClientScreens() {
    }
}
