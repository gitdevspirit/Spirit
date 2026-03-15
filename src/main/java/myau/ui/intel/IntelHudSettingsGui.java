// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package myau.ui.intel;

import java.io.IOException;
import myau.Myau;
import myau.module.modules.LobbyIntel;
import myau.ui.clickgui.RoundedUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class IntelHudSettingsGui extends GuiScreen {
   private final IntelHudOverlay hudOverlay;
   private final GuiScreen parent;
   private static final int POPUP_WIDTH = 280;
   private static final int POPUP_HEIGHT = 420;
   private static final int PADDING = 12;
   private static final int LINE_HEIGHT = 20;
   private static final int SLIDER_HEIGHT = 28;
   private static final int TOGGLE_HEIGHT = 24;
   private static final int BG_POPUP = -301331950;
   private static final int BG_HEADER = -15395555;
   private static final int ACCENT = -1470024;
   private static final int TEXT_BRIGHT = -2236946;
   private static final int TEXT_DIM = -7829351;
   private static final int DIVIDER = 872415231;
   private int scrollOffset = 0;
   private int maxScroll = 0;
   private boolean draggingSlider = false;
   private int draggingSliderIndex = -1;

   public IntelHudSettingsGui(IntelHudOverlay hudOverlay, GuiScreen parent) {
      this.hudOverlay = hudOverlay;
      this.parent = parent;
   }

   public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
      func_73734_a(0, 0, this.field_146294_l, this.field_146295_m, -872415232);
      ScaledResolution sr = new ScaledResolution(this.field_146297_k);
      int sw = sr.func_78326_a();
      int sh = sr.func_78328_b();
      int popupX = (sw - 280) / 2;
      int popupY = (sh - 420) / 2;
      RoundedUtils.drawRoundedRect((float)popupX, (float)popupY, 280.0F, 420.0F, 6.0F, -301331950);
      RoundedUtils.drawRoundedRect((float)popupX, (float)popupY, 280.0F, 36.0F, 6.0F, -15395555);
      func_73734_a(popupX, popupY + 30, popupX + 280, popupY + 36, -15395555);
      GlStateManager.func_179094_E();
      GlStateManager.func_179109_b((float)(popupX + 12), (float)(popupY + 12), 0.0F);
      GlStateManager.func_179152_a(1.1F, 1.1F, 1.0F);
      this.field_146297_k.field_71466_p.func_175065_a("HUD Overlay", 0.0F, 0.0F, -1470024, false);
      GlStateManager.func_179121_F();
      int closeX = popupX + 280 - 28;
      int closeY = popupY + 10;
      boolean closeHover = mouseX >= closeX && mouseX < closeX + 18 && mouseY >= closeY && mouseY < closeY + 18;
      this.field_146297_k.field_71466_p.func_175065_a("X", (float)(closeX + 5), (float)(closeY + 5), closeHover ? -48060 : -2236946, false);
      GlStateManager.func_179094_E();
      GL11.glEnable(3089);
      int scale = sr.func_78325_e();
      int contentY = popupY + 46;
      int contentH = 364;
      GL11.glScissor(popupX * scale, (sh - contentY - contentH) * scale, 280 * scale, contentH * scale);
      int y = contentY - this.scrollOffset + 12;
      int innerX = popupX + 12;
      int innerW = 256;
      y = this.drawToggle(innerX, y, innerW, "Enabled", this.hudOverlay.isEnabled(), mouseX, mouseY);
      y += 6;
      this.drawDivider(innerX, y, innerW);
      y += 12;
      this.drawLabel(innerX, y, "POSITION");
      y += 16;
      y = this.drawSlider(innerX, y, innerW, "X Position", this.hudOverlay.getPosX(), 0, 1920, mouseX, mouseY, 0);
      y = this.drawSlider(innerX, y, innerW, "Y Position", this.hudOverlay.getPosY(), 0, 1080, mouseX, mouseY, 1);
      y += 6;
      this.drawDivider(innerX, y, innerW);
      y += 12;
      this.drawLabel(innerX, y, "DISPLAY");
      y += 16;
      y = this.drawSlider(innerX, y, innerW, "Scale", (int)(this.hudOverlay.getScale() * 100.0F), 50, 200, mouseX, mouseY, 2);
      y = this.drawSlider(innerX, y, innerW, "Max Players", this.hudOverlay.getMaxPlayers(), 1, 20, mouseX, mouseY, 3);
      y = this.drawSlider(innerX, y, innerW, "Background Opacity", this.hudOverlay.getBgOpacity(), 0, 255, mouseX, mouseY, 4);
      y = this.drawSlider(innerX, y, innerW, "Border Opacity", this.hudOverlay.getBorderOpacity(), 0, 255, mouseX, mouseY, 5);
      y += 6;
      this.drawDivider(innerX, y, innerW);
      y += 12;
      this.drawLabel(innerX, y, "COLUMNS");
      y += 20;
      y = this.drawToggle(innerX, y, innerW, "Player Heads", this.hudOverlay.getShowHeads(), mouseX, mouseY);
      y = this.drawToggle(innerX, y, innerW, "Star", this.hudOverlay.getShowStar(), mouseX, mouseY);
      y = this.drawToggle(innerX, y, innerW, "FKDR", this.hudOverlay.getShowFkdr(), mouseX, mouseY);
      y = this.drawToggle(innerX, y, innerW, "WLR", this.hudOverlay.getShowWlr(), mouseX, mouseY);
      y = this.drawToggle(innerX, y, innerW, "Winstreak", this.hudOverlay.getShowStreak(), mouseX, mouseY);
      y = this.drawToggle(innerX, y, innerW, "Urchin Icon", this.hudOverlay.getShowUrchin(), mouseX, mouseY);
      y = this.drawToggle(innerX, y, innerW, "Threat Score", this.hudOverlay.getShowThreat(), mouseX, mouseY);
      y = this.drawToggle(innerX, y, innerW, "Team Colors", this.hudOverlay.getShowTeamColor(), mouseX, mouseY);
      y += 8;
      this.drawDivider(innerX, y, innerW);
      y += 16;
      this.drawLabel(innerX, y, "SORTING");
      y += 20;
      y = this.drawDropdown(innerX, y, innerW, "Sort By", new String[]{"Threat", "FKDR", "Name"}, this.hudOverlay.getSortMode().equals("threat") ? 0 : (this.hudOverlay.getSortMode().equals("fkdr") ? 1 : 2), mouseX, mouseY);
      int contentHeight = y - (contentY + 12) + 12;
      this.maxScroll = Math.max(0, contentHeight - contentH + 24);
      GL11.glDisable(3089);
      GlStateManager.func_179121_F();
      super.func_73863_a(mouseX, mouseY, partialTicks);
   }

   private void drawLabel(int x, int y, String text) {
      this.field_146297_k.field_71466_p.func_175065_a(text, (float)x, (float)y, -7829351, false);
   }

   private void drawDivider(int x, int y, int w) {
      func_73734_a(x, y, x + w, y + 1, 872415231);
   }

   private int drawToggle(int x, int y, int w, String label, boolean value, int mx, int my) {
      boolean hover = mx >= x && mx < x + w && my >= y && my < y + 24;
      this.field_146297_k.field_71466_p.func_175065_a(label, (float)x, (float)(y + 8), hover ? -2236946 : -7829351, false);
      int toggleW = 40;
      int toggleH = 20;
      int toggleX = x + w - toggleW;
      int toggleY = y + 4;
      int bgColor = value ? 1156157880 : 860111957;
      RoundedUtils.drawRoundedRect((float)toggleX, (float)toggleY, (float)toggleW, (float)toggleH, (float)(toggleH / 2), bgColor);
      int knobSize = 16;
      int knobX = value ? toggleX + toggleW - knobSize - 2 : toggleX + 2;
      int knobY = toggleY + 2;
      RoundedUtils.drawRoundedRect((float)knobX, (float)knobY, (float)knobSize, (float)knobSize, (float)(knobSize / 2), value ? -1470024 : -10066313);
      return y + 24;
   }

   private int drawSlider(int x, int y, int w, String label, int value, int min, int max, int mx, int my, int sliderIndex) {
      this.field_146297_k.field_71466_p.func_175065_a(label, (float)x, (float)y, -2236946, false);
      y += 10;
      String valStr = String.valueOf(value);
      this.field_146297_k.field_71466_p.func_175065_a(valStr, (float)(x + w - this.field_146297_k.field_71466_p.func_78256_a(valStr)), (float)y, -1470024, false);
      int barW = w - 30;
      int barH = 8;
      func_73734_a(x, y, x + barW, y + barH, 1715749956);
      float pct = (float)(value - min) / (float)(max - min);
      int fillW = Math.max(2, (int)((float)barW * pct));
      func_73734_a(x, y, x + fillW, y + barH, -1470024);
      int knobSize = 12;
      int knobX = x + fillW - knobSize / 2;
      int knobY = y - 2;
      func_73734_a(knobX, knobY, knobX + knobSize, knobY + knobSize, -1);
      func_73734_a(knobX + 2, knobY + 2, knobX + knobSize - 2, knobY + knobSize - 2, -1470024);
      return y + 20;
   }

   private int drawDropdown(int x, int y, int w, String label, String[] options, int selected, int mx, int my) {
      this.field_146297_k.field_71466_p.func_175065_a(label, (float)x, (float)y, -7829351, false);
      y += 12;
      int btnH = 24;
      boolean hover = mx >= x && mx < x + w && my >= y && my < y + btnH;
      RoundedUtils.drawRoundedRect((float)x, (float)y, (float)w, (float)btnH, 4.0F, hover ? 1157627903 : 587202559);
      this.field_146297_k.field_71466_p.func_175065_a(options[selected], (float)(x + 12), (float)(y + 8), -2236946, false);
      this.field_146297_k.field_71466_p.func_175065_a("v", (float)(x + w - 20), (float)(y + 8), -7829351, false);
      return y + btnH + 8;
   }

   protected void func_73864_a(int mouseX, int mouseY, int mouseButton) throws IOException {
      ScaledResolution sr = new ScaledResolution(this.field_146297_k);
      int sw = sr.func_78326_a();
      int sh = sr.func_78328_b();
      int popupX = (sw - 280) / 2;
      int popupY = (sh - 420) / 2;
      int closeX = popupX + 280 - 28;
      int closeY = popupY + 10;
      if (mouseX >= closeX && mouseX < closeX + 18 && mouseY >= closeY && mouseY < closeY + 18) {
         this.saveAndClose();
      } else if (mouseX >= popupX && mouseX <= popupX + 280 && mouseY >= popupY && mouseY <= popupY + 420) {
         int contentY = popupY + 46;
         int y = contentY - this.scrollOffset + 12;
         int innerX = popupX + 12;
         int innerW = 256;
         if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + 24) {
            this.hudOverlay.setEnabled(!this.hudOverlay.isEnabled());
         } else {
            y += 58;
            if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + 28) {
               int val = (int)((float)(mouseX - innerX) / (float)innerW * 1920.0F);
               this.hudOverlay.setPosition(Math.max(0, Math.min(1920, val)), this.hudOverlay.getPosY());
               this.draggingSlider = true;
               this.draggingSliderIndex = 0;
            } else {
               y += 28;
               if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + 28) {
                  int val = (int)((float)(mouseX - innerX) / (float)innerW * 1080.0F);
                  this.hudOverlay.setPosition(this.hudOverlay.getPosX(), Math.max(0, Math.min(1080, val)));
                  this.draggingSlider = true;
                  this.draggingSliderIndex = 1;
               } else {
                  y += 62;
                  if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + 28) {
                     int val = 50 + (int)((float)(mouseX - innerX) / (float)innerW * 150.0F);
                     this.hudOverlay.setScale(Math.max(0.5F, Math.min(2.0F, (float)val / 100.0F)));
                     this.draggingSlider = true;
                     this.draggingSliderIndex = 2;
                  } else {
                     y += 28;
                     if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + 28) {
                        int val = 1 + (int)((float)(mouseX - innerX) / (float)innerW * 19.0F);
                        this.hudOverlay.setMaxPlayers(Math.max(1, Math.min(20, val)));
                        this.draggingSlider = true;
                        this.draggingSliderIndex = 3;
                     } else {
                        y += 28;
                        if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + 28) {
                           int val = (int)((float)(mouseX - innerX) / (float)innerW * 255.0F);
                           this.hudOverlay.setBgOpacity(Math.max(0, Math.min(255, val)));
                           this.draggingSlider = true;
                           this.draggingSliderIndex = 4;
                        } else {
                           y += 28;
                           if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + 28) {
                              int val = (int)((float)(mouseX - innerX) / (float)innerW * 255.0F);
                              this.hudOverlay.setBorderOpacity(Math.max(0, Math.min(255, val)));
                              this.draggingSlider = true;
                              this.draggingSliderIndex = 5;
                           } else {
                              y += 62;
                              if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + 24) {
                                 this.hudOverlay.setShowHeads(!this.hudOverlay.getShowHeads());
                              } else {
                                 y += 24;
                                 if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + 24) {
                                    this.hudOverlay.setShowStar(!this.hudOverlay.getShowStar());
                                 } else {
                                    y += 24;
                                    if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + 24) {
                                       this.hudOverlay.setShowFkdr(!this.hudOverlay.getShowFkdr());
                                    } else {
                                       y += 24;
                                       if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + 24) {
                                          this.hudOverlay.setShowWlr(!this.hudOverlay.getShowWlr());
                                       } else {
                                          y += 24;
                                          if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + 24) {
                                             this.hudOverlay.setShowStreak(!this.hudOverlay.getShowStreak());
                                          } else {
                                             y += 24;
                                             if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + 24) {
                                                this.hudOverlay.setShowUrchin(!this.hudOverlay.getShowUrchin());
                                             } else {
                                                y += 24;
                                                if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + 24) {
                                                   this.hudOverlay.setShowThreat(!this.hudOverlay.getShowThreat());
                                                } else {
                                                   y += 24;
                                                   if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y && mouseY < y + 24) {
                                                      this.hudOverlay.setShowTeamColor(!this.hudOverlay.getShowTeamColor());
                                                   } else {
                                                      y += 68;
                                                      if (mouseX >= innerX && mouseX < innerX + innerW && mouseY >= y + 12 && mouseY < y + 12 + 24) {
                                                         String mode = this.hudOverlay.getSortMode();
                                                         String newMode = mode.equals("threat") ? "fkdr" : (mode.equals("fkdr") ? "name" : "threat");
                                                         this.hudOverlay.setSortMode(newMode);
                                                      } else {
                                                         super.func_73864_a(mouseX, mouseY, mouseButton);
                                                      }
                                                   }
                                                }
                                             }
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      } else {
         this.saveAndClose();
      }
   }

   protected void func_146286_b(int mouseX, int mouseY, int state) {
      this.draggingSlider = false;
      this.draggingSliderIndex = -1;
      super.func_146286_b(mouseX, mouseY, state);
   }

   protected void func_146273_a(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
      if (this.draggingSlider && this.draggingSliderIndex >= 0) {
         ScaledResolution sr = new ScaledResolution(this.field_146297_k);
         int sw = sr.func_78326_a();
         int popupX = (sw - 280) / 2;
         int innerX = popupX + 12;
         int innerW = 226;
         switch (this.draggingSliderIndex) {
            case 0:
               int valX = (int)((float)(mouseX - innerX) / (float)innerW * 1920.0F);
               this.hudOverlay.setPosition(Math.max(0, Math.min(1920, valX)), this.hudOverlay.getPosY());
               break;
            case 1:
               int valY = (int)((float)(mouseX - innerX) / (float)innerW * 1080.0F);
               this.hudOverlay.setPosition(this.hudOverlay.getPosX(), Math.max(0, Math.min(1080, valY)));
               break;
            case 2:
               int valScale = 50 + (int)((float)(mouseX - innerX) / (float)innerW * 150.0F);
               this.hudOverlay.setScale(Math.max(0.5F, Math.min(2.0F, (float)valScale / 100.0F)));
               break;
            case 3:
               int valMax = 1 + (int)((float)(mouseX - innerX) / (float)innerW * 19.0F);
               this.hudOverlay.setMaxPlayers(Math.max(1, Math.min(20, valMax)));
               break;
            case 4:
               int valOpacity = (int)((float)(mouseX - innerX) / (float)innerW * 255.0F);
               this.hudOverlay.setBgOpacity(Math.max(0, Math.min(255, valOpacity)));
               break;
            case 5:
               int valBorder = (int)((float)(mouseX - innerX) / (float)innerW * 255.0F);
               this.hudOverlay.setBorderOpacity(Math.max(0, Math.min(255, valBorder)));
         }

      }
   }

   public void func_146274_d() throws IOException {
      super.func_146274_d();
      int dw = Mouse.getEventDWheel();
      if (dw != 0) {
         this.scrollOffset -= dw > 0 ? 20 : -20;
         this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, this.maxScroll));
      }

   }

   public boolean func_73868_f() {
      return false;
   }

   private void saveAndClose() {
      LobbyIntel lobbyIntel = (LobbyIntel)Myau.moduleManager.getModule("LobbyIntel");
      if (lobbyIntel != null) {
         lobbyIntel.saveHudSettings();
      }

      this.field_146297_k.func_147108_a(this.parent);
   }
}
