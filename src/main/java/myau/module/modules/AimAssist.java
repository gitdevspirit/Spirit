package myau.module.modules;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ItemUtil;
import myau.util.PlayerUtil;
import myau.util.RandomUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class AimAssist extends Module {
   private static final Minecraft mc = Minecraft.func_71410_x();
   private final TimerUtil timer = new TimerUtil();
   private float smoothedYaw = Float.NaN;
   private float smoothedPitch = Float.NaN;
   public final DropdownSetting mode = (DropdownSetting)this.register(new DropdownSetting("Mode", 0, new String[]{"ASSIST", "SILENT"}));
   public final SliderSetting hSpeed = (SliderSetting)this.register(new SliderSetting("H-Speed", 2.0, 0.0, 10.0, 0.1));
   public final SliderSetting vSpeed = (SliderSetting)this.register(new SliderSetting("V-Speed", 0.0, 0.0, 10.0, 0.1));
   public final SliderSetting smoothing = (SliderSetting)this.register(new SliderSetting("Smoothing", 70.0, 0.0, 100.0, 1.0));
   public final SliderSetting range = (SliderSetting)this.register(new SliderSetting("Range", 4.5, 3.0, 8.0, 0.1));
   public final SliderSetting fov = (SliderSetting)this.register(new SliderSetting("FOV", 90.0, 30.0, 360.0, 1.0));
   public final BooleanSetting weaponOnly = (BooleanSetting)this.register(new BooleanSetting("Weapons Only", true));
   public final BooleanSetting allowTools = (BooleanSetting)this.register(new BooleanSetting("Allow Tools", false));
   public final BooleanSetting botChecks = (BooleanSetting)this.register(new BooleanSetting("Bot Check", true));
   public final BooleanSetting team = (BooleanSetting)this.register(new BooleanSetting("Teams", true));

   public AimAssist() {
      super("AimAssist", false);
   }

   public void onDisabled() {
      this.smoothedYaw = Float.NaN;
      this.smoothedPitch = Float.NaN;
   }

   private boolean isValidTarget(EntityPlayer p) {
      if (p != mc.field_71439_g && p != mc.field_71439_g.field_70154_o) {
         if (p != mc.func_175606_aa() && p != mc.func_175606_aa().field_70154_o) {
            if (p.field_70725_aQ > 0) {
               return false;
            } else if (RotationUtil.distanceToEntity(p) > this.range.getValue()) {
               return false;
            } else if (RotationUtil.angleToEntity(p) > (float)this.fov.getValue()) {
               return false;
            } else if (RotationUtil.rayTrace(p) != null) {
               return false;
            } else if (TeamUtil.isFriend(p)) {
               return false;
            } else {
               return (!this.team.getValue() || !TeamUtil.isSameTeam(p)) && (!this.botChecks.getValue() || !TeamUtil.isBot(p));
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean isInReach(EntityPlayer p) {
      Reach reach = (Reach)Myau.moduleManager.modules.get(Reach.class);
      double distance = reach.isEnabled() ? reach.range.getValue() : 3.0;
      return RotationUtil.distanceToEntity(p) <= distance;
   }

   private boolean isLookingAtBlock() {
      return mc.field_71476_x != null && mc.field_71476_x.field_72313_a == MovingObjectType.BLOCK;
   }

   private float[] advanceSmoothed(AxisAlignedBB box) {
      if (Float.isNaN(this.smoothedYaw)) {
         this.smoothedYaw = mc.field_71439_g.field_70177_z;
      }

      if (Float.isNaN(this.smoothedPitch)) {
         this.smoothedPitch = mc.field_71439_g.field_70125_A;
      }

      float[] ideal = RotationUtil.getRotationsToBox(box, this.smoothedYaw, this.smoothedPitch, 180.0F, 0.0F);
      float sm = (float)this.smoothing.getValue() / 100.0F;
      float lerpT = MathHelper.func_76131_a(1.0F - sm * 0.95F + RandomUtil.nextFloat(-0.01F, 0.01F), 0.04F, 1.0F);
      float yawDiff = MathHelper.func_76142_g(ideal[0] - this.smoothedYaw);
      float pitchDiff = ideal[1] - this.smoothedPitch;
      float maxH = (float)this.hSpeed.getValue();
      float maxV = (float)this.vSpeed.getValue();
      yawDiff = MathHelper.func_76131_a(yawDiff * lerpT, -maxH, maxH);
      pitchDiff = maxV > 0.0F ? MathHelper.func_76131_a(pitchDiff * lerpT, -maxV, maxV) : 0.0F;
      this.smoothedYaw = RotationUtil.quantizeAngle(this.smoothedYaw + yawDiff);
      this.smoothedPitch = RotationUtil.quantizeAngle(MathHelper.func_76131_a(this.smoothedPitch + pitchDiff, -90.0F, 90.0F));
      return new float[]{this.smoothedYaw, this.smoothedPitch};
   }

   @EventTarget
   public void onTick(TickEvent event) {
      if (this.isEnabled() && this.mode.getIndex() == 0) {
         if (event.getType() == EventType.POST && mc.field_71462_r == null) {
            if (!this.weaponOnly.getValue() || ItemUtil.hasRawUnbreakingEnchant() || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
               boolean attacking = PlayerUtil.isAttacking();
               if ((!attacking || !this.isLookingAtBlock()) && (attacking || !this.timer.hasTimeElapsed(350L))) {
                  List<EntityPlayer> inRange = (List)mc.field_71441_e.field_72996_f.stream().filter((e) -> {
                     return e instanceof EntityPlayer;
                  }).map((e) -> {
                     return (EntityPlayer)e;
                  }).filter(this::isValidTarget).sorted(Comparator.comparingDouble(RotationUtil::distanceToEntity)).collect(Collectors.toList());
                  if (inRange.isEmpty()) {
                     this.smoothedYaw = Float.NaN;
                     this.smoothedPitch = Float.NaN;
                     return;
                  }

                  if (inRange.stream().anyMatch(this::isInReach)) {
                     inRange.removeIf((p) -> {
                        return !this.isInReach(p);
                     });
                  }

                  EntityPlayer player = (EntityPlayer)inRange.get(0);
                  if (RotationUtil.distanceToEntity(player) <= 0.0) {
                     return;
                  }

                  AxisAlignedBB bb = player.func_174813_aQ().func_72314_b((double)player.func_70111_Y(), (double)player.func_70111_Y(), (double)player.func_70111_Y());
                  float[] r = this.advanceSmoothed(bb);
                  Myau.rotationManager.setRotation(r[0], r[1], 0, false);
               }
            }

         }
      }
   }

   @EventTarget
   public void onUpdate(UpdateEvent event) {
      if (this.isEnabled() && this.mode.getIndex() == 1) {
         if (event.getType() == EventType.PRE && mc.field_71462_r == null) {
            if (!this.weaponOnly.getValue() || ItemUtil.hasRawUnbreakingEnchant() || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
               List<EntityPlayer> inRange = (List)mc.field_71441_e.field_72996_f.stream().filter((e) -> {
                  return e instanceof EntityPlayer;
               }).map((e) -> {
                  return (EntityPlayer)e;
               }).filter(this::isValidTarget).sorted(Comparator.comparingDouble(RotationUtil::distanceToEntity)).collect(Collectors.toList());
               if (inRange.isEmpty()) {
                  this.smoothedYaw = Float.NaN;
                  this.smoothedPitch = Float.NaN;
                  return;
               }

               EntityPlayer player = (EntityPlayer)inRange.get(0);
               if (RotationUtil.distanceToEntity(player) <= 0.0) {
                  return;
               }

               AxisAlignedBB bb = player.func_174813_aQ().func_72314_b((double)player.func_70111_Y(), (double)player.func_70111_Y(), (double)player.func_70111_Y());
               float[] r = this.advanceSmoothed(bb);
               event.setRotation(r[0], r[1], 1);
            }

         }
      }
   }

   @EventTarget
   public void onPress(KeyEvent event) {
      if (event.getKey() == mc.field_71474_y.field_74312_F.func_151463_i() && !((Module)Myau.moduleManager.modules.get(AutoClicker.class)).isEnabled()) {
         this.timer.reset();
      }

   }
}
