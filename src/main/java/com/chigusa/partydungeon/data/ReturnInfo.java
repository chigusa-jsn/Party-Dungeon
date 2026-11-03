package com.chigusa.partydungeon.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/** 作業場やダンジョンに入る前の場所とゲームモード。出るときに元へ戻すために使う。 */
public record ReturnInfo(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot,
        GameType gameMode) {

    public static ReturnInfo of(ServerPlayer p) {
        return new ReturnInfo(p.level().dimension(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot(),
                p.gameMode.getGameModeForPlayer());
    }

    /** プレイヤーを元の場所・ゲームモードに戻す */
    public void restore(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            level = server.overworld();
            var spawn = level.getSharedSpawnPos();
            p.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, yRot, xRot);
        } else {
            p.teleportTo(level, x, y, z, yRot, xRot);
        }
        p.setGameMode(gameMode);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("dim", dimension.location().toString());
        t.putDouble("x", x);
        t.putDouble("y", y);
        t.putDouble("z", z);
        t.putFloat("yRot", yRot);
        t.putFloat("xRot", xRot);
        t.putString("mode", gameMode.getName());
        return t;
    }

    public static ReturnInfo load(CompoundTag t) {
        return new ReturnInfo(
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dim"))),
                t.getDouble("x"), t.getDouble("y"), t.getDouble("z"),
                t.getFloat("yRot"), t.getFloat("xRot"),
                GameType.byName(t.getString("mode"), GameType.SURVIVAL));
    }
}
