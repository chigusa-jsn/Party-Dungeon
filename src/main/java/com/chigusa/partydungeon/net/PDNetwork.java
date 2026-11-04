package com.chigusa.partydungeon.net;

import com.chigusa.partydungeon.PartyDungeon;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 専用画面のための通信。
 * サーバー → クライアント: 「この画面をこのデータで開いて」
 * クライアント → サーバー: 「画面でこのボタンが押された」
 */
public final class PDNetwork {

    /** サーバー → クライアント */
    public record OpenScreen(String screen, CompoundTag data) implements CustomPacketPayload {
        public static final Type<OpenScreen> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(PartyDungeon.MOD_ID, "open_screen"));
        public static final StreamCodec<ByteBuf, OpenScreen> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OpenScreen::screen,
                ByteBufCodecs.COMPOUND_TAG, OpenScreen::data,
                OpenScreen::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** クライアント → サーバー */
    public record ScreenAction(String screen, String action, CompoundTag data) implements CustomPacketPayload {
        public static final Type<ScreenAction> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(PartyDungeon.MOD_ID, "screen_action"));
        public static final StreamCodec<ByteBuf, ScreenAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ScreenAction::screen,
                ByteBufCodecs.STRING_UTF8, ScreenAction::action,
                ByteBufCodecs.COMPOUND_TAG, ScreenAction::data,
                ScreenAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var reg = event.registrar("2");
        reg.playToServer(OpenMenuPayload.TYPE, OpenMenuPayload.STREAM_CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                com.chigusa.partydungeon.menu.Menus.main(sp.server, sp);
            }
        }));
        reg.playToClient(OpenScreen.TYPE, OpenScreen.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                com.chigusa.partydungeon.client.ClientScreens.open(payload.screen(), payload.data());
            }
        }));
        reg.playToServer(ScreenAction.TYPE, ScreenAction.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                ScreenActions.handle(sp, payload.screen(), payload.action(), payload.data());
            }
        }));
    }

    public static void openScreen(ServerPlayer p, String screen, CompoundTag data) {
        PacketDistributor.sendToPlayer(p, new OpenScreen(screen, data));
    }

    /** クライアントから呼ぶ */
    public static void sendAction(String screen, String action, CompoundTag data) {
        PacketDistributor.sendToServer(new ScreenAction(screen, action, data));
    }

    private PDNetwork() {
    }
}
