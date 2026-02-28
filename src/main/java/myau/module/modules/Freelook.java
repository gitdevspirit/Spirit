package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class Freelook extends Module {
   private static final Minecraft mc = Minecraft.func_71410_x();
   public final DropdownSetting mode = (DropdownSetting)this.register(new DropdownSetting("Mode", 0, new String[]{"Toggle", "Hold"}));
   public final DropdownSetting perspective = (DropdownSetting)this.register(new DropdownSetting("Perspective", 0, new String[]{"Current", "First", "Third", "Reverse"}));
   public final BooleanSetting invertPitch = (BooleanSetting)this.register(new BooleanSetting("Invert Pitch", false));
   public final BooleanSetting invertYaw = (BooleanSetting)this.register(new BooleanSetting("Invert Yaw", false));
   public final BooleanSetting lockPitch = (BooleanSetting)this.register(new BooleanSetting("Lock Pitch", false));
   public final BooleanSetting smooth = (BooleanSetting)this.register(new BooleanSetting("Smooth", true));
   public final SliderSetting smoothSpeed = (SliderSetting)this.register(new SliderSetting("Smooth Speed", (double)8.0F, (double)1.0F, (double)20.0F, (double)0.5F));
   public final SliderSetting sensitivity = (SliderSetting)this.register(new SliderSetting("Sensitivity", (double)1.0F, 0.1, (double)3.0F, 0.05));
   public final SliderSetting keyBind = (SliderSetting)this.register(new SliderSetting("Key (LWJGL)", (double)56.0F, (double)1.0F, (double)200.0F, (double)1.0F));
   public static float cameraYaw = 0.0F;
   public static float cameraPitch = 0.0F;
   public static boolean active = false;
   private int savedPerspective = 0;
   private boolean wasHeld = false;
   private boolean returning = false;
   private float returnStartYaw = 0.0F;
   private float returnStartPitch = 0.0F;
   private long returnStartMs = 0L;
   private static final long RETURN_MS = 300L;

   public Freelook() {
      super("Freelook", false);
   }

   public void onEnabled() {
      if (mc.field_71439_g != null) {
         cameraYaw = mc.field_71439_g.field_70177_z;
         cameraPitch = mc.field_71439_g.field_70125_A;
         this.returning = false;
         active = true;
         this.savedPerspective = mc.field_71474_y.field_74320_O;
         int p = this.perspective.getIndex();
         if (p == 1) {
            mc.field_71474_y.field_74320_O = 0;
         } else if (p == 2) {
            mc.field_71474_y.field_74320_O = 1;
         } else if (p == 3) {
            mc.field_71474_y.field_74320_O = 2;
         }

      }
   }

   public void onDisabled() {
      if (this.smooth.getValue()) {
         this.returning = true;
         this.returnStartYaw = cameraYaw;
         this.returnStartPitch = cameraPitch;
         this.returnStartMs = System.currentTimeMillis();
      } else {
         if (mc.field_71439_g != null) {
            cameraYaw = mc.field_71439_g.field_70177_z;
            cameraPitch = mc.field_71439_g.field_70125_A;
         }

         active = false;
      }

      mc.field_71474_y.field_74320_O = this.savedPerspective;
      this.wasHeld = false;
   }

   @EventTarget
   public void onTick(TickEvent event) {
      if (event.getType() == EventType.PRE) {
         if (mc.field_71439_g != null) {
            int key = (int)this.keyBind.getValue();
            if (this.isEnabled() && this.mode.getIndex() == 1) {
               boolean held = Keyboard.isKeyDown(key);
               if (this.wasHeld && !held) {
                  this.disable();
                  return;
               }

               this.wasHeld = held;
            }

            if (this.returning && mc.field_71439_g != null) {
               long elapsed = System.currentTimeMillis() - this.returnStartMs;
               float t = Math.min(1.0F, (float)elapsed / 300.0F);
               float ease = t == 0.0F ? 0.0F : (float)Math.pow((double)2.0F, (double)10.0F * ((double)t - (double)1.0F));
               float targetYaw = mc.field_71439_g.field_70177_z;
               float targetPitch = mc.field_71439_g.field_70125_A;
               cameraYaw = lerp(this.returnStartYaw, targetYaw, ease);
               cameraPitch = lerp(this.returnStartPitch, targetPitch, ease);
               if (t >= 1.0F) {
                  this.returning = false;
                  active = false;
               }

            } else if (this.isEnabled()) {
               if (mc.field_71415_G) {
                  GameSettings gs = mc.field_71474_y;
                  float sens = (float)this.sensitivity.getValue();
                  float gSens = gs.field_74341_c * 0.6F + 0.2F;
                  float factor = gSens * gSens * gSens * 8.0F * sens;
                  int dx = Mouse.getDX();
                  int dy = Mouse.getDY();
                  float dyaw = (float)dx * factor * (this.invertYaw.getValue() ? -1.0F : 1.0F);
                  float dpitch = (float)(-dy) * factor * (this.invertPitch.getValue() ? -1.0F : 1.0F);
                  cameraYaw = (cameraYaw + dyaw) % 360.0F;
                  if (!this.lockPitch.getValue()) {
                     cameraPitch = clamp(cameraPitch + dpitch, -90.0F, 90.0F);
                  }
               }

            }
         }
      }
   }

   @EventTarget
   public void onRender2D(Render2DEvent event) {
      if (!this.isEnabled() && this.mode.getIndex() == 1) {
         int key = (int)this.keyBind.getValue();
         if (Keyboard.isKeyDown(key) && !this.wasHeld) {
            this.wasHeld = true;
            this.enable();
         }
      }

   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   private static float clamp(float v, float min, float max) {
      return Math.max(min, Math.min(max, v));
   }

   private void enable() {
      if (!this.isEnabled()) {
         this.toggle();
      }

   }

   private void disable() {
      if (this.isEnabled()) {
         this.toggle();
      }

   }

   public String[] getSuffix() {
      return active ? new String[]{this.mode.getIndex() == 0 ? "Toggle" : "Hold"} : new String[0];
   }
}
