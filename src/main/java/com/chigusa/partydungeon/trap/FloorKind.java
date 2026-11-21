package com.chigusa.partydungeon.trap;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/** 状態異常の床の種類 */
public enum FloorKind {
    SPIKE("spike_floor", "トゲの床", "Spike Floor", null, 0, 0, 0xB8B8C0, 0),
    FIRE("fire_floor", "炎の床", "Fire Floor", null, 0, 0, 0xFF6020, 8),
    POISON("poison_floor", "毒の床", "Poison Floor", MobEffects.POISON, 0, 80, 0x60D040, 0),
    SLOW("slow_floor", "鈍足の床", "Slowness Floor", MobEffects.MOVEMENT_SLOWDOWN, 2, 40, 0x6080A0, 0),
    WEAK("weak_floor", "弱体化の床", "Weakness Floor", MobEffects.WEAKNESS, 0, 100, 0x806080, 0),
    BLIND("blind_floor", "暗闇の床", "Blindness Floor", MobEffects.BLINDNESS, 0, 60, 0x202030, 0),
    WITHER("wither_floor", "ウィザーの床", "Wither Floor", MobEffects.WITHER, 0, 60, 0x303030, 0),
    NAUSEA("nausea_floor", "混乱の床", "Nausea Floor", MobEffects.CONFUSION, 0, 140, 0xA040C0, 0),
    HUNGER("hunger_floor", "空腹の床", "Hunger Floor", MobEffects.HUNGER, 1, 100, 0x8A6A30, 0),
    LEVITATE("levitate_floor", "浮遊の床", "Levitation Floor", MobEffects.LEVITATION, 1, 25, 0xE0E0FF, 4),
    SPEED("speed_floor", "加速の床", "Speed Floor", MobEffects.MOVEMENT_SPEED, 2, 60, 0x40C0FF, 6),
    JUMP("jump_floor", "ジャンプ強化の床", "Jump Boost Floor", MobEffects.JUMP, 2, 60, 0x80FF60, 6),
    HEAL("heal_floor", "回復の床", "Healing Floor", MobEffects.REGENERATION, 1, 60, 0xFF80B0, 10);

    public final String id;
    public final String ja;
    public final String en;
    @Nullable
    public final Holder<MobEffect> effect;
    public final int amplifier;
    public final int duration;
    public final int color;
    public final int light;

    FloorKind(String id, String ja, String en, @Nullable Holder<MobEffect> effect, int amplifier, int duration,
            int color, int light) {
        this.id = id;
        this.ja = ja;
        this.en = en;
        this.effect = effect;
        this.amplifier = amplifier;
        this.duration = duration;
        this.color = color;
        this.light = light;
    }
}
