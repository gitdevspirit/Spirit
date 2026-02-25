package myau.module;

import java.util.ArrayList;
import java.util.List;
import myau.Myau;
import myau.module.modules.HUD;
import myau.util.KeyBindUtil;

public abstract class Module {
   protected final String name;
   protected final boolean defaultEnabled;
   protected final int defaultKey;
   protected final boolean defaultHidden;
   protected boolean enabled;
   protected int key;
   protected boolean hidden;
   protected final List<Setting> settings;

   public Module(String name, boolean enabled) {
      this(name, enabled, false);
   }

   public Module(String name, boolean enabled2, boolean hidden) {
      this.settings = new ArrayList();
      this.name = name;
      this.enabled = this.defaultEnabled = enabled2;
      this.key = this.defaultKey = 0;
      this.hidden = this.defaultHidden = hidden;
   }

   protected <T extends Setting> T register(T setting) {
      this.settings.add(setting);
      return setting;
   }

   public List<Setting> getSettings() {
      return this.settings;
   }

   public String getName() {
      return this.name;
   }

   public String formatModule() {
      return String.format("%s%s &r(%s&r)", this.key == 0 ? "" : String.format("&l[%s] &r", KeyBindUtil.getKeyName(this.key)), this.name, this.enabled ? "&a&lON" : "&c&lOFF");
   }

   public String[] getSuffix() {
      return new String[0];
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void setEnabled(boolean enabled) {
      if (this.enabled != enabled) {
         this.enabled = enabled;
         if (enabled) {
            this.onEnabled();
         } else {
            this.onDisabled();
         }
      }

   }

   public boolean toggle() {
      boolean enabled = !this.enabled;
      this.setEnabled(enabled);
      if (this.enabled == enabled) {
         if ((Boolean)((HUD)Myau.moduleManager.modules.get(HUD.class)).toggleSound.getValue()) {
            Myau.moduleManager.playSound();
         }

         try {
            if (Myau.notificationManager != null) {
               String action = this.enabled ? "was toggled successfully" : "was untoggled successfully";
               int color = this.enabled ? '\uff00' : 16711680;
               Myau.notificationManager.add(this.getName() + " " + action, color);
            }
         } catch (Exception var4) {
         }

         return true;
      } else {
         return false;
      }
   }

   public int getKey() {
      return this.key;
   }

   public void setKey(int integer) {
      this.key = integer;
   }

   public boolean isHidden() {
      return this.hidden;
   }

   public void setHidden(boolean boolean1) {
      this.hidden = boolean1;
   }

   public void onEnabled() {
   }

   public void onDisabled() {
   }

   public void verifyValue(String string) {
   }
}
