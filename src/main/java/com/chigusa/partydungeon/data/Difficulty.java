package com.chigusa.partydungeon.data;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** 難易度。入るときに敵の強さと数が変わり、報酬も難易度ごとに分かれる。 */
public enum Difficulty {
    EASY("easy", "イージー", ChatFormatting.GREEN, Items.LIME_WOOL, 0.6, 0.6, 0.75, 0.0),
    NORMAL("normal", "ノーマル", ChatFormatting.AQUA, Items.LIGHT_BLUE_WOOL, 1.0, 1.0, 1.0, 0.0),
    HARD("hard", "ハード", ChatFormatting.YELLOW, Items.YELLOW_WOOL, 1.6, 1.4, 1.25, 0.0),
    VERY_HARD("very_hard", "ベリーハード", ChatFormatting.RED, Items.RED_WOOL, 2.5, 2.0, 1.5, 0.1),
    CHAOS("chaos", "カオス", ChatFormatting.DARK_PURPLE, Items.PURPLE_WOOL, 4.0, 3.0, 2.0, 0.3);

    public final String id;
    public final String label;
    public final ChatFormatting color;
    public final Item icon;
    /** 敵の体力の倍率 */
    public final double health;
    /** 敵の攻撃力の倍率 */
    public final double damage;
    /** 敵の数の倍率 */
    public final double count;
    /** 敵の移動速度の上乗せ（0.3 = 30% 速い） */
    public final double speed;

    Difficulty(String id, String label, ChatFormatting color, Item icon, double health, double damage, double count,
            double speed) {
        this.id = id;
        this.label = label;
        this.color = color;
        this.icon = icon;
        this.health = health;
        this.damage = damage;
        this.count = count;
        this.speed = speed;
    }

    /** 置いた数 → この難易度で実際に出す数（最低1） */
    public int scaleCount(int base) {
        return Math.max(1, (int) Math.round(base * count));
    }

    public Difficulty next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public Difficulty prev() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }

    public static Difficulty byId(String id) {
        for (Difficulty d : values()) {
            if (d.id.equals(id)) {
                return d;
            }
        }
        return NORMAL;
    }
}
