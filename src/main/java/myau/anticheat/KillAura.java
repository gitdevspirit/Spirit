// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package myau.anticheat;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;

public class KillAura {
   private final Map<String, Long> lastAttackTime = new HashMap();
   private final Map<String, Integer> consecutiveHeadshots = new HashMap();
   private static final Minecraft mc = Minecraft.func_71410_x();

   public KillAura() {
   }

   @SubscribeEvent
   public void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase == Phase.END && mc.field_71439_g != null && mc.field_71441_e != null) {
         World world = mc.field_71441_e;
         long currentTick = world.func_82737_E();

         for(EntityPlayer player : world.field_73010_i) {
            if (player != mc.field_71439_g) {
               Vec3 playerPos = new Vec3(player.field_70165_t, player.field_70163_u + (double)player.func_70047_e(), player.field_70161_v);
               Vec3 clientPos = new Vec3(mc.field_71439_g.field_70165_t, mc.field_71439_g.field_70163_u + (double)mc.field_71439_g.func_70047_e(), mc.field_71439_g.field_70161_v);
               double distance = playerPos.func_72438_d(clientPos);
               if (distance < (double)6.0F && distance > 0.1) {
                  boolean isAttacking = player.field_70733_aJ > 0.0F && player.field_70732_aI == 0.0F;
                  if (isAttacking) {
                     long lastAttack = (Long)this.lastAttackTime.getOrDefault(player.func_70005_c_(), currentTick);
                     long timeSinceLastAttack = currentTick - lastAttack;
                     if (timeSinceLastAttack > 0L && timeSinceLastAttack < 3L) {
                        flag.receiveSignal(player.func_70005_c_(), "KillAura");
                     }

                     this.lastAttackTime.put(player.func_70005_c_(), currentTick);
                     int headshots = (Integer)this.consecutiveHeadshots.getOrDefault(player.func_70005_c_(), 0);
                     ++headshots;
                     this.consecutiveHeadshots.put(player.func_70005_c_(), headshots);
                     if (headshots > 8) {
                        flag.receiveSignal(player.func_70005_c_(), "KillAura");
                        this.consecutiveHeadshots.put(player.func_70005_c_(), 0);
                     }
                  } else {
                     this.consecutiveHeadshots.put(player.func_70005_c_(), 0);
                  }
               }
            }
         }
      }

   }
}
