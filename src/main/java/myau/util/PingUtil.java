package myau.util;

import net.minecraft.client.Minecraft;

public class PingUtil {
    public static int getPing() {
        if (Minecraft.getMinecraft().thePlayer == null) return 0;
        return Minecraft.getMinecraft().getNetHandler().getPlayerInfo(
                Minecraft.getMinecraft().thePlayer.getUniqueID()
        ).getResponseTime();
    }
}
