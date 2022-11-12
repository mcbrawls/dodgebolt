package dev.andante.dodgebolt.util;

import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public interface TitleHelper {
    static void sendTimes(ServerPlayerEntity player, int fadeIn, int stay, int fadeOut) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeIn, stay, fadeOut));
    }

    static void sendTitle(ServerPlayerEntity player, Text title, Text subtitle) {
        ServerPlayNetworkHandler handler = player.networkHandler;
        handler.sendPacket(new TitleS2CPacket(title));
        handler.sendPacket(new SubtitleS2CPacket(subtitle));
    }
}
