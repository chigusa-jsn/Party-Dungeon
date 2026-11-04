package com.chigusa.partydungeon.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import javax.annotation.Nullable;

import com.chigusa.partydungeon.net.PDNetwork;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

/** 敵の出現ブロックの設定画面：モブを検索して選び、数と出るタイミングを入力 */
public class SpawnerScreen extends PDScreen {
    private record Mob(ResourceLocation id, String name, ItemStack icon) {
    }

    private static final int ROW = 20;

    private final long pos;
    @Nullable
    private ResourceLocation selected;
    private final int initCount;
    private final int initRadius;
    private final boolean initBoss;

    private final List<Mob> all = new ArrayList<>();
    private final List<String> namespaces = new ArrayList<>();
    private List<Mob> shown = new ArrayList<>();
    private int nsIndex;
    private double scroll;

    private EditBox search;
    private EditBox count;
    private EditBox radius;
    private Checkbox boss;
    private Button nsButton;
    private int listX;
    private int listY;
    private int listW;
    private int listH;

    public SpawnerScreen(CompoundTag data) {
        super("敵の出現ブロック");
        this.pos = data.getLong("pos");
        this.selected = data.contains("entity") ? ResourceLocation.tryParse(data.getString("entity")) : null;
        this.initCount = data.contains("count") ? data.getInt("count") : 1;
        this.initRadius = data.getInt("radius");
        this.initBoss = data.getBoolean("boss");

        TreeSet<String> ns = new TreeSet<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type == EntityType.PLAYER || !type.canSummon()) {
                continue;
            }
            SpawnEggItem egg = SpawnEggItem.byId(type);
            boolean living = type.getCategory() != MobCategory.MISC || egg != null || type == EntityType.WITHER
                    || type == EntityType.ENDER_DRAGON || type == EntityType.IRON_GOLEM || type == EntityType.SNOW_GOLEM;
            if (!living) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            all.add(new Mob(id, type.getDescription().getString(), new ItemStack(egg != null ? egg : Items.SPAWNER)));
            ns.add(id.getNamespace());
        }
        all.sort(Comparator.comparing((Mob m) -> !m.id.getNamespace().equals("minecraft"))
                .thenComparing(m -> m.id.toString()));
        namespaces.add("");
        if (ns.remove("minecraft")) {
            namespaces.add("minecraft");
        }
        namespaces.addAll(ns);
    }

    @Override
    protected void init() {
        int w = Math.min(360, width - 20);
        int x = (width - w) / 2;
        listX = x;
        listW = w;
        listY = 46;
        listH = Math.max(60, height - listY - 64);

        search = new EditBox(font, x, 24, w / 2 - 2, 18, Component.literal("検索"));
        search.setHint(Component.literal("検索（日本語名・英語ID）"));
        search.setResponder(s -> refilter());
        addRenderableWidget(search);
        nsButton = button(nsLabel(), x + w / 2 + 2, 24, w / 2 - 2, 18, b -> {
            nsIndex = (nsIndex + 1) % namespaces.size();
            b.setMessage(Component.literal(nsLabel()));
            refilter();
        });

        int by = listY + listH + 6;
        count = numberBox(x + 30, by, 36, initCount, null);
        radius = numberBox(x + 170, by, 36, initRadius, null);
        boss = addRenderableWidget(Checkbox.builder(Component.literal("ボス"), font).pos(x + 230, by).selected(initBoss)
                .build());
        button("保存", x + w - 150, by + 22, 72, 20, b -> save());
        button("キャンセル", x + w - 74, by + 22, 74, 20, b -> onClose());
        refilter();
        setInitialFocus(search);
    }

    private String nsLabel() {
        String ns = namespaces.get(nsIndex);
        return "MOD: " + (ns.isEmpty() ? "すべて" : ns);
    }

    private void refilter() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        String ns = namespaces.get(nsIndex);
        shown = new ArrayList<>();
        for (Mob m : all) {
            if (!ns.isEmpty() && !m.id.getNamespace().equals(ns)) {
                continue;
            }
            if (!q.isEmpty() && !m.id.toString().contains(q) && !m.name.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            shown.add(m);
        }
        scroll = 0;
    }

    private void save() {
        CompoundTag t = new CompoundTag();
        t.putLong("pos", pos);
        if (selected != null) {
            t.putString("entity", selected.toString());
        }
        t.putInt("count", parse(count, 1, 1, 64));
        t.putInt("radius", parse(radius, 0, 0, 128));
        t.putBoolean("boss", boss.selected());
        PDNetwork.sendAction("spawner", "save", t);
        onClose();
    }

    private int maxScroll() {
        return Math.max(0, shown.size() * ROW - listH);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - sy * ROW * 2));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int i = (int) ((my - listY + scroll) / ROW);
            if (i >= 0 && i < shown.size()) {
                selected = shown.get(i).id;
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
        int first = (int) (scroll / ROW);
        for (int i = first; i < shown.size(); i++) {
            int y = listY + i * ROW - (int) scroll;
            if (y > listY + listH) {
                break;
            }
            Mob m = shown.get(i);
            boolean sel = m.id.equals(selected);
            boolean hover = mx >= listX && mx < listX + listW && my >= y && my < y + ROW && my >= listY
                    && my < listY + listH;
            if (sel) {
                g.fill(listX, y, listX + listW, y + ROW, 0xFF2E5E2E);
            } else if (hover) {
                g.fill(listX, y, listX + listW, y + ROW, 0xFF303030);
            }
            g.renderItem(m.icon, listX + 3, y + 2);
            text(g, m.name, listX + 24, y + 6, sel ? GREEN : WHITE);
            String id = m.id.toString();
            text(g, id, listX + listW - font.width(id) - 6, y + 6, GRAY);
        }
        g.disableScissor();
        if (shown.isEmpty()) {
            g.drawCenteredString(font, "見つかりません", listX + listW / 2, listY + 20, GRAY);
        }
        int by = listY + listH + 6;
        text(g, "数", listX, by + 5, WHITE);
        text(g, "出る距離", listX + 120, by + 5, WHITE);
        text(g, "0 = 開始と同時。数字 = その距離まで近づいたら出る", listX, by + 28, GRAY);
        String sel = selected == null ? "（まだ選ばれていません）" : selected.toString();
        text(g, "選択中: " + sel, listX, by + 40, selected == null ? RED : GREEN);
    }
}
