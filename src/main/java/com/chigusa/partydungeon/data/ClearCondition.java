package com.chigusa.partydungeon.data;

/** ダンジョンのクリア条件。ダンジョンごとに選べる。 */
public enum ClearCondition {
    BOSS("boss", "ボスを全部倒す"),
    ALL("all", "敵を全部倒す"),
    GOAL("goal", "ゴール地点に着く");

    public final String id;
    public final String label;

    ClearCondition(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public static ClearCondition byId(String id) {
        for (ClearCondition c : values()) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return ALL;
    }
}
