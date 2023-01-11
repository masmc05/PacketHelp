package me.masmc05.packetlogger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.DecoderException;
import io.papermc.paper.network.ChannelInitializeListenerHolder;
import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.NonNull;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public final class PacketLogger extends JavaPlugin {
    private final long offset;
    private final Unsafe unsafe;

    public PacketLogger() throws ReflectiveOperationException {
        Field ffield = null;
        for (var field : ClientboundPlayerInfoUpdatePacket.class.getDeclaredFields()) {
            if (field.getType() == List.class) {
                ffield = field;
                field.setAccessible(true);
            }
        }
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        unsafe = (Unsafe) unsafeField.get(null);
        offset = unsafe.objectFieldOffset(Objects.requireNonNull(ffield));
    }
    private void afterInitChannel(@NonNull Channel c) {
        c.pipeline().addBefore("packet_handler", "info_debugger_bbb", new Listener());
    }

    @Override
    public void onEnable() {
        ChannelInitializeListenerHolder.addListener(new NamespacedKey(this, "listen"), this::afterInitChannel);
    }
    private final class Listener extends ChannelDuplexHandler {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (msg instanceof ClientboundPlayerInfoUpdatePacket packet && packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) {
                var buf = new FriendlyByteBuf(UnpooledByteBufAllocator.DEFAULT.directBuffer());
                var players = new LinkedList<ClientboundPlayerInfoUpdatePacket.Entry>();
                for (var entry : packet.entries()){
                    try {
                        buf.writeUtf(entry.profile().getName(), 16);
                        buf.writeGameProfileProperties(entry.profile().getProperties());
                        GameProfile gameProfile = new GameProfile(entry.profileId(), buf.readUtf(16));
                        gameProfile.getProperties().putAll(buf.readGameProfileProperties());
                        players.add(entry);
                    } catch (Throwable e) {
                        Bukkit.getScheduler().runTask(PacketLogger.this, () -> {
                            Player player = Bukkit.getPlayer(entry.profile().getId());
                            if (player == null) return;
                            player.kick(Component.text("Bad data!"));
                        });
                    }
                }
                if (players.size() != packet.entries().size()) {
                    unsafe.putObject(packet, offset, players);
                }
            }
            ctx.write(msg, promise);
        }
    }
}
