package com.chigusa.partydungeon.net;

import com.chigusa.partydungeon.PartyDungeon;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** クライアントで P キーを押したとき「メイン画面を開いて」とサーバーに送る合図 */
public record OpenMenuPayload() implements CustomPacketPayload {
    public static final OpenMenuPayload INSTANCE = new OpenMenuPayload();
    public static final Type<OpenMenuPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PartyDungeon.MOD_ID, "open_menu"));
    public static final StreamCodec<ByteBuf, OpenMenuPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
