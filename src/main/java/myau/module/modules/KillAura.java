package myau.module.modules;

import com.google.common.base.CaseFormat;
import java.awt.Color;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;
import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.AttackEvent;
import myau.events.CancelUseEvent;
import myau.events.HitBlockEvent;
import myau.events.LeftClickMouseEvent;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.RightClickMouseEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.management.RotationState;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.BooleanSetting;
import myau.module.DropdownSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.util.ChatUtil;
import myau.util.ItemUtil;
import myau.util.KeyBindUtil;
import myau.util.MoveUtil;
import myau.util.PacketUtil;
import myau.util.PlayerUtil;
import myau.util.RandomUtil;
import myau.util.RenderUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;

public class KillAura extends Module {
   private static final Minecraft mc = Minecraft.getMinecraft();
   private static final DecimalFormat df;
   private final TimerUtil timer = new TimerUtil();
   private AttackData target = null;
   private int switchTick = 0;
   private boolean hitRegistered = false;
   private boolean blockingState = false;
   private boolean isBlocking = false;
   private boolean fakeBlockState = false;
   private boolean blinkReset = false;
   private boolean swapped = false;
   private long attackDelayMS = 0L;
   private int blockTick = 0;
   private int lastTickProcessed = 0;
   public static int attackCooldownTicks = 0;

   // ── Attack ────────────────────────────────────────────────────────────────
   public final DropdownSetting mode       = register(new DropdownSetting("Mode", 0, "Single", "Switch"));
   public final DropdownSetting sort       = register(new DropdownSetting("Sort", 0, "Distance", "Health", "Hurt Time", "FOV"));
   public final SliderSetting   attackRange= register(new SliderSetting("Attack Range",  3.0, 3.0, 6.0, 0.1));
   public final SliderSetting   swingRange = register(new SliderSetting("Swing Range",   3.5, 3.0, 6.0, 0.1));
   public final SliderSetting   minCPS     = register(new SliderSetting("Min APS",       14,  1,   20,  1));
   public final SliderSetting   maxCPS     = register(new SliderSetting("Max APS",       14,  1,   20,  1));
   public final SliderSetting   switchDelay= register(new SliderSetting("Switch Delay",  150, 0,   1000,1));
   public final SliderSetting   fov        = register(new SliderSetting("FOV",           360, 30,  360, 1));

   // ── Rotation ──────────────────────────────────────────────────────────────
   public final DropdownSetting rotations  = register(new DropdownSetting("Rotations", 2, "None", "Legit", "Silent", "Lock View"));
   public final DropdownSetting moveFix    = register(new DropdownSetting("Move Fix",  1, "None", "Silent", "Strict"));
   public final SliderSetting   smoothing  = register(new SliderSetting("Smoothing",   0,   0,   100, 1));
   public final SliderSetting   angleStep  = register(new SliderSetting("Angle Step",  90,  30,  180, 1));

   // ── Auto Block ────────────────────────────────────────────────────────────
   public final DropdownSetting autoBlock  = register(new DropdownSetting("Auto Block", 3,
           "None", "Vanilla", "Spoof", "Grim", "Blink", "Interact", "Swap", "Legit", "Fake", "New", "BlinkLess", "Rise", "Opal"));
   public final BooleanSetting  autoBlockRequirePress = register(new BooleanSetting("AB Require Press", false));
   public final SliderSetting   autoBlockCPS   = register(new SliderSetting("AB APS",   10.0, 1.0, 20.0, 0.1));
   public final SliderSetting   autoBlockRange = register(new SliderSetting("AB Range",  6.0, 3.0, 8.0,  0.1));

   // ── Filters ───────────────────────────────────────────────────────────────
   public final BooleanSetting throughWalls  = register(new BooleanSetting("Through Walls", true));
   public final BooleanSetting requirePress  = register(new BooleanSetting("Require Press",  false));
   public final BooleanSetting allowMining   = register(new BooleanSetting("Allow Mining",   true));
   public final BooleanSetting weaponsOnly   = register(new BooleanSetting("Weapons Only",   true));
   public final BooleanSetting allowTools    = register(new BooleanSetting("Allow Tools",     false));
   public final BooleanSetting inventoryCheck= register(new BooleanSetting("Inventory Check", true));
   public final BooleanSetting botCheck      = register(new BooleanSetting("Bot Check",       true));

   // ── Targets ───────────────────────────────────────────────────────────────
   public final BooleanSetting players    = register(new BooleanSetting("Players",    true));
   public final BooleanSetting bosses     = register(new BooleanSetting("Bosses",     false));
   public final BooleanSetting mobs       = register(new BooleanSetting("Mobs",       false));
   public final BooleanSetting animals    = register(new BooleanSetting("Animals",    false));
   public final BooleanSetting golems     = register(new BooleanSetting("Golems",     false));
   public final BooleanSetting silverfish = register(new BooleanSetting("Silverfish", false));
   public final BooleanSetting teams      = register(new BooleanSetting("Teams",      true));

   // ── Display ───────────────────────────────────────────────────────────────
   public final DropdownSetting showTarget = register(new DropdownSetting("Show Target", 0, "None", "Default", "HUD"));
   public final DropdownSetting debugLog   = register(new DropdownSetting("Debug Log",   0, "None", "Health"));

   public KillAura() {
      super("KillAura", false);
   }

   private long getAttackDelay() {
      return this.isBlocking ? (long)(1000.0F / (double)this.autoBlockCPS.getValue()) : 1000L / RandomUtil.nextLong((long)this.minCPS.getValue(), (long)this.maxCPS.getValue());
   }

   private boolean performAttack(float yaw, float pitch) {
      if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
         if (this.isPlayerBlocking() && (int)(double)this.autoBlock.getIndex() != 1) {
            return false;
         } else if (this.attackDelayMS > 0L) {
            return false;
         } else {
            this.attackDelayMS += this.getAttackDelay();
            mc.thePlayer.swingItem();
            if (((int)(double)this.rotations.getIndex() != 0 || !this.isBoxInAttackRange(this.target.getBox())) && RotationUtil.rayTrace(this.target.getBox(), yaw, pitch, this.attackRange.getValue()) == null) {
               return false;
            } else {
               AttackEvent event = new AttackEvent(this.target.getEntity());
               EventManager.call(event);
               ((IAccessorPlayerControllerMP)mc.playerController).callSyncCurrentPlayItem();
               PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.ATTACK));
               if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
                  PlayerUtil.attackEntity(this.target.getEntity());
               }
               this.hitRegistered = true;
               return true;
            }
         }
      } else {
         return false;
      }
   }

   private void sendUseItem() {
      ((IAccessorPlayerControllerMP)mc.playerController).callSyncCurrentPlayItem();
      this.startBlock(mc.thePlayer.getHeldItem());
   }

   private void startBlock(ItemStack itemStack) {
      PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
      mc.thePlayer.setItemInUse(itemStack, itemStack.getMaxItemUseDuration());
      this.blockingState = true;
   }

   private void stopBlock() {
      PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
      mc.thePlayer.stopUsingItem();
      this.blockingState = false;
   }

   private void interactAttack(float yaw, float pitch) {
      if (this.target != null) {
         MovingObjectPosition mop = RotationUtil.rayTrace(this.target.getBox(), yaw, pitch, 8.0);
         if (mop != null) {
            ((IAccessorPlayerControllerMP)mc.playerController).callSyncCurrentPlayItem();
            PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), new Vec3(mop.hitVec.xCoord - this.target.getX(), mop.hitVec.yCoord - this.target.getY(), mop.hitVec.zCoord - this.target.getZ())));
            PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.INTERACT));
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
            mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
            this.blockingState = true;
         }
      }
   }

   private boolean canAttack() {
      if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)) {
         return false;
      }
      if (this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) {
         return false;
      } else if (!this.weaponsOnly.getValue() || ItemUtil.hasRawUnbreakingEnchant() || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
         if (((IAccessorPlayerControllerMP)mc.playerController).getIsHittingBlock()) {
            return false;
         } else if ((ItemUtil.isEating() || ItemUtil.isUsingBow()) && PlayerUtil.isUsingItem()) {
            return false;
         } else {
            AutoHeal autoHeal = (AutoHeal)Myau.moduleManager.modules.get(AutoHeal.class);
            if (autoHeal.isEnabled() && autoHeal.isSwitching()) {
               return false;
            } else {
               BedNuker bedNuker = (BedNuker)Myau.moduleManager.modules.get(BedNuker.class);
               if (bedNuker.isEnabled() && bedNuker.isReady()) {
                  return false;
               } else if (((Module)Myau.moduleManager.modules.get(Scaffold.class)).isEnabled()) {
                  return false;
               } else if (this.requirePress.getValue()) {
                  return PlayerUtil.isAttacking();
               } else {
                  return !this.allowMining.getValue() || !mc.objectMouseOver.typeOfHit.equals(MovingObjectType.BLOCK) || !PlayerUtil.isAttacking();
               }
            }
         }
      } else {
         return false;
      }
   }

   private boolean canAutoBlock() {
      if (!ItemUtil.isHoldingSword()) {
         return false;
      } else {
         return !this.autoBlockRequirePress.getValue() || PlayerUtil.isUsingItem();
      }
   }

   public boolean hasValidTarget() {
      return mc.theWorld.loadedEntityList.stream().anyMatch((entity) -> entity instanceof EntityLivingBase && this.isValidTarget((EntityLivingBase)entity) && this.isInBlockRange((EntityLivingBase)entity));
   }

   private boolean isValidTarget(EntityLivingBase e) {
      if (!mc.theWorld.loadedEntityList.contains(e)) {
         return false;
      } else if (e == mc.thePlayer || e == mc.thePlayer.ridingEntity) {
         return false;
      } else if (e == mc.getRenderViewEntity() || e == mc.getRenderViewEntity().ridingEntity) {
         return false;
      } else if (RotationUtil.angleToEntity(e) > (float)(double)this.fov.getValue()) {
         return false;
      } else if (!this.throughWalls.getValue() && RotationUtil.rayTrace(e) != null) {
         return false;
      } else if (e instanceof EntityOtherPlayerMP) {
         if (!this.players.getValue()) return false;
         if (TeamUtil.isFriend((EntityPlayer)e)) return false;
         return (!this.teams.getValue() || !TeamUtil.isSameTeam((EntityPlayer)e))
             && (!this.botCheck.getValue() || !TeamUtil.isBot((EntityPlayer)e));
      } else if (e instanceof EntityDragon || e instanceof EntityWither) {
         return this.bosses.getValue();
      } else if (e instanceof EntityMob || e instanceof EntitySlime) {
         if (e instanceof EntitySilverfish)
            return this.silverfish.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(e));
         return this.mobs.getValue();
      } else if (e instanceof EntityAnimal || e instanceof EntityBat || e instanceof EntitySquid || e instanceof EntityVillager) {
         return this.animals.getValue();
      } else if (e instanceof EntityIronGolem) {
         return false;
      } else {
         return this.golems.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(e));
      }
   }

   private boolean isInRange(EntityLivingBase e) {
      return this.isInBlockRange(e) || this.isInSwingRange(e) || this.isInAttackRange(e);
   }

   private boolean isInBlockRange(EntityLivingBase e) {
      return RotationUtil.distanceToEntity(e) <= this.autoBlockRange.getValue();
   }

   private boolean isInSwingRange(EntityLivingBase e) {
      return RotationUtil.distanceToEntity(e) <= this.swingRange.getValue();
   }

   private boolean isBoxInSwingRange(AxisAlignedBB box) {
      return RotationUtil.distanceToBox(box) <= this.swingRange.getValue();
   }

   private boolean isInAttackRange(EntityLivingBase e) {
      return RotationUtil.distanceToEntity(e) <= this.attackRange.getValue();
   }

   private boolean isBoxInAttackRange(AxisAlignedBB box) {
      return RotationUtil.distanceToBox(box) <= this.attackRange.getValue();
   }

   private boolean isPlayerTarget(EntityLivingBase e) {
      return e instanceof EntityPlayer && TeamUtil.isTarget((EntityPlayer)e);
   }

   private int findEmptySlot(int currentSlot) {
      for (int i = 0; i < 9; i++) {
         if (i != currentSlot && mc.thePlayer.inventory.getStackInSlot(i) == null) return i;
      }
      for (int i = 0; i < 9; i++) {
         if (i != currentSlot) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (s != null && !s.isItemEnchanted()) return i;
         }
      }
      return Math.floorMod(currentSlot - 1, 9);
   }

   private int findSwordSlot(int currentSlot) {
      for (int i = 0; i < 9; i++) {
         if (i != currentSlot) {
            ItemStack item = mc.thePlayer.inventory.getStackInSlot(i);
            if (item != null && item.getItem() instanceof ItemSword) return i;
         }
      }
      return -1;
   }

   public EntityLivingBase getTarget() {
      return this.target != null ? this.target.getEntity() : null;
   }

   public boolean isAttackAllowed() {
      Scaffold scaffold = (Scaffold)Myau.moduleManager.modules.get(Scaffold.class);
      if (scaffold.isEnabled()) return false;
      if (!this.weaponsOnly.getValue() || ItemUtil.hasRawUnbreakingEnchant() || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
         return !this.requirePress.getValue() || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
      }
      return false;
   }

   public boolean shouldAutoBlock() {
      if (this.isPlayerBlocking() && this.isBlocking) {
         int ab = this.autoBlock.getIndex();
         return !mc.thePlayer.isInWater() && !mc.thePlayer.isOnLadder() && (ab == 3 || ab == 4 || ab == 5 || ab == 6 || ab == 7);
      }
      return false;
   }

   public boolean isBlocking() {
      return this.fakeBlockState && ItemUtil.isHoldingSword();
   }

   public boolean isPlayerBlocking() {
      return (mc.thePlayer.isUsingItem() || this.blockingState) && ItemUtil.isHoldingSword();
   }

   @EventTarget(3)
   public void onUpdate(UpdateEvent event) {
      if (event.getType() == EventType.POST && this.blinkReset) {
         this.blinkReset = false;
         Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
         Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
      }

      if (!this.isEnabled() || event.getType() != EventType.PRE || mc.currentScreen != null) return;

      if (this.attackDelayMS > 0L) this.attackDelayMS -= 50L;

      boolean attack = this.target != null && this.canAttack();
      boolean block = attack && this.canAutoBlock();
      if (!block) {
         Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
         this.isBlocking = false;
         this.fakeBlockState = false;
         this.blockTick = 0;
      }

      if (!attack) return;

      boolean swap = false;
      boolean blocked = false;
      if (block) {
         switch (this.autoBlock.getIndex()) {
            case 0:
               if (PlayerUtil.isUsingItem()) {
                  this.isBlocking = true;
                  if (!this.isPlayerBlocking() && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) swap = true;
               } else {
                  this.isBlocking = false;
                  if (this.isPlayerBlocking() && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) this.stopBlock();
               }
               Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
               this.fakeBlockState = false;
               break;
            case 1:
               if (this.hasValidTarget()) {
                  if (!this.isPlayerBlocking() && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) swap = true;
                  Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                  this.isBlocking = true;
                  this.fakeBlockState = false;
               } else {
                  Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                  this.isBlocking = false;
                  this.fakeBlockState = false;
               }
               break;
            case 2: {
               if (!this.hasValidTarget()) {
                  Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                  this.isBlocking = false;
                  this.fakeBlockState = false;
                  break;
               }
               int item = ((IAccessorPlayerControllerMP)mc.playerController).getCurrentPlayerItem();
               if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing && mc.thePlayer.inventory.currentItem == item && (!this.isPlayerBlocking() || this.blockTick == 0) && (this.attackDelayMS <= 0L || this.attackDelayMS > 50L)) {
                  int slot = this.findEmptySlot(item);
                  PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
                  PacketUtil.sendPacket(new C09PacketHeldItemChange(item));
                  swap = true;
                  this.blockTick = 1;
               } else {
                  this.blockTick = 0;
               }
               Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
               this.isBlocking = true;
               this.fakeBlockState = false;
               break;
            }
            case 3:
               if (this.hasValidTarget()) {
                  if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                     switch (this.blockTick) {
                        case 0: if (!this.isPlayerBlocking()) swap = true; this.blinkReset = true; this.blockTick = 1; break;
                        case 1: if (this.isPlayerBlocking()) { this.stopBlock(); attack = false; } if (this.attackDelayMS <= 80L) this.blockTick = 0; break;
                        default: this.blockTick = 0;
                     }
                  }
                  this.isBlocking = true; this.fakeBlockState = true;
               } else {
                  Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                  this.isBlocking = false; this.fakeBlockState = false;
               }
               break;
            case 4:
               if (this.hasValidTarget()) {
                  if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                     switch (this.blockTick) {
                        case 0: if (!this.isPlayerBlocking()) swap = true; this.blinkReset = true; this.blockTick = 1; break;
                        case 1: if (this.isPlayerBlocking()) { this.stopBlock(); attack = false; } if (this.attackDelayMS <= 50L) this.blockTick = 0; break;
                        default: this.blockTick = 0;
                     }
                  }
                  this.isBlocking = true; this.fakeBlockState = true;
               } else {
                  Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                  this.isBlocking = false; this.fakeBlockState = false;
               }
               break;
            case 5: {
               if (this.hasValidTarget()) {
                  int item = ((IAccessorPlayerControllerMP)mc.playerController).getCurrentPlayerItem();
                  if (mc.thePlayer.inventory.currentItem == item && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                     switch (this.blockTick) {
                        case 0: if (!this.isPlayerBlocking()) swap = true; this.blinkReset = true; this.blockTick = 1; break;
                        case 1:
                           if (this.isPlayerBlocking()) {
                              int slot = this.findEmptySlot(item);
                              PacketUtil.sendPacket(new C09PacketHeldItemChange(slot));
                              ((IAccessorPlayerControllerMP)mc.playerController).setCurrentPlayerItem(slot);
                              attack = false;
                           }
                           if (this.attackDelayMS <= 50L) this.blockTick = 0;
                           break;
                        default: this.blockTick = 0;
                     }
                  }
                  this.isBlocking = true; this.fakeBlockState = true;
               } else {
                  Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                  this.isBlocking = false; this.fakeBlockState = false;
               }
               break;
            }
            case 6: {
               if (this.hasValidTarget()) {
                  int item = ((IAccessorPlayerControllerMP)mc.playerController).getCurrentPlayerItem();
                  if (mc.thePlayer.inventory.currentItem == item && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                     switch (this.blockTick) {
                        case 0: { int slot = this.findSwordSlot(item); if (slot != -1) { if (!this.isPlayerBlocking()) swap = true; this.blockTick = 1; } break; }
                        case 1: {
                           int swordsSlot = this.findSwordSlot(item);
                           if (swordsSlot == -1) { this.blockTick = 0; }
                           else if (!this.isPlayerBlocking()) { swap = true; }
                           else if (this.attackDelayMS <= 50L) {
                              PacketUtil.sendPacket(new C09PacketHeldItemChange(swordsSlot));
                              ((IAccessorPlayerControllerMP)mc.playerController).setCurrentPlayerItem(swordsSlot);
                              this.startBlock(mc.thePlayer.inventory.getStackInSlot(swordsSlot));
                              attack = false; this.blockTick = 0;
                           }
                           break;
                        }
                        default: this.blockTick = 0;
                     }
                     Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                     this.isBlocking = true; this.fakeBlockState = true;
                     break;
                  }
               }
               Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
               this.isBlocking = false; this.fakeBlockState = false;
               break;
            }
            case 7:
               if (this.hasValidTarget()) {
                  if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                     switch (this.blockTick) {
                        case 0: if (!this.isPlayerBlocking()) swap = true; this.blockTick = 1; break;
                        case 1: if (this.isPlayerBlocking()) { this.stopBlock(); attack = false; } if (this.attackDelayMS <= 50L) this.blockTick = 0; break;
                        default: this.blockTick = 0;
                     }
                  }
                  Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                  this.isBlocking = true; this.fakeBlockState = false;
               } else {
                  Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                  this.isBlocking = false; this.fakeBlockState = false;
               }
               break;
            case 8:
               Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
               this.isBlocking = false;
               this.fakeBlockState = this.hasValidTarget();
               if (PlayerUtil.isUsingItem() && !this.isPlayerBlocking() && !Myau.playerStateManager.digging && !Myau.playerStateManager.placing) swap = true;
               break;
            case 9:
               if (this.hasValidTarget()) {
                  if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                     switch (this.blockTick) {
                        case 0: this.setCurrentSlot(); if (!this.isPlayerBlocking()) swap = true; blocked = true; this.blockTick = 1; break;
                        case 1: this.stopBlock(); attack = false; this.setNextSlot(); if (this.attackDelayMS <= 50L) this.blockTick = 0; break;
                        default: this.blockTick = 0; this.setCurrentSlot();
                     }
                  }
                  this.isBlocking = true; this.fakeBlockState = true;
               } else {
                  if (this.blockTick == 1 && this.isPlayerBlocking()) { this.stopBlock(); this.setNextSlot(); }
                  this.blockTick = 0; this.setCurrentSlot();
                  Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                  this.isBlocking = false; this.fakeBlockState = false;
               }
               break;
            case 10:
               if (this.hasValidTarget()) {
                  if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                     switch (this.blockTick) {
                        case 0: this.setCurrentSlot(); if (!this.isPlayerBlocking()) swap = true; blocked = true; this.blockTick = 1; break;
                        case 1: {
                           if (this.isPlayerBlocking()) this.stopBlock();
                           attack = false;
                           int emptySlot = this.findEmptySlot(mc.thePlayer.inventory.currentItem);
                           PacketUtil.sendPacket(new C09PacketHeldItemChange(emptySlot));
                           this.swapped = true;
                           if (this.attackDelayMS <= 50L) this.blockTick = 0;
                           break;
                        }
                        default: this.blockTick = 0; this.setCurrentSlot();
                     }
                  }
                  this.isBlocking = true; this.fakeBlockState = true;
               } else {
                  if (this.blockTick == 1 && this.isPlayerBlocking()) { this.stopBlock(); this.setCurrentSlot(); }
                  this.blockTick = 0;
                  Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                  this.isBlocking = false; this.fakeBlockState = false;
               }
               break;
            case 11:
               if (!this.hasValidTarget()) {
                  Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                  this.isBlocking = false; this.fakeBlockState = false;
                  break;
               }
               if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                  switch (this.blockTick) {
                     case 0: if (!this.isPlayerBlocking()) swap = true; blocked = true; this.blockTick = 1; break;
                     case 1:
                        if (this.isPlayerBlocking()) {
                           if (((Module)Myau.moduleManager.modules.get(NoSlow.class)).isEnabled()) {
                              int randomSlot;
                              do { randomSlot = new Random().nextInt(9); } while (randomSlot == mc.thePlayer.inventory.currentItem);
                              PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                              PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                           }
                           this.stopBlock(); attack = false;
                        }
                        if (this.attackDelayMS <= 50L) this.blockTick = 0;
                        break;
                     default: this.blockTick = 0;
                  }
               }
               this.isBlocking = true; this.fakeBlockState = true;
               break;
            case 12:
               if (this.hasValidTarget()) {
                  if (!Myau.playerStateManager.digging && !Myau.playerStateManager.placing) {
                     switch (this.blockTick) {
                        case 0: this.setCurrentSlot(); if (!this.isPlayerBlocking()) swap = true; blocked = true; this.blockTick = 1; break;
                        case 1: if (this.isPlayerBlocking()) { this.stopBlock(); attack = false; } if (this.attackDelayMS <= 50L) { this.setNextSlot(); this.blockTick = 0; } break;
                        default: this.blockTick = 0; this.setCurrentSlot();
                     }
                  }
                  this.isBlocking = true; this.fakeBlockState = true;
               } else {
                  if (this.blockTick == 1 && this.isPlayerBlocking()) { this.stopBlock(); this.setNextSlot(); }
                  this.blockTick = 0; this.setCurrentSlot();
                  Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                  this.isBlocking = false; this.fakeBlockState = false;
               }
               break;
         }
      }

      boolean attacked = false;
      if (this.isBoxInSwingRange(this.target.getBox())) {
         if (this.rotations.getIndex() == 2 || this.rotations.getIndex() == 3) {
            float[] rots = RotationUtil.getRotationsToBox(this.target.getBox(), event.getYaw(), event.getPitch(),
                    (float)this.angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F),
                    (float)this.smoothing.getValue() / 100.0F);
            event.setRotation(rots[0], rots[1], 1);
            if (this.rotations.getIndex() == 3) {
               Myau.rotationManager.setRotation(rots[0], rots[1], 1, true);
            }
            if (this.moveFix.getIndex() != 0 || this.rotations.getIndex() == 3) {
               event.setPervRotation(rots[0], 1);
            }
         }
         if (attack) attacked = this.performAttack(event.getNewYaw(), event.getNewPitch());
      }

      if (swap) {
         if (attacked) this.interactAttack(event.getNewYaw(), event.getNewPitch());
         else this.sendUseItem();
      }

      if (blocked) {
         Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
         Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
      }
   }

   @EventTarget
   public void onTick(TickEvent event) {
      if (!this.isEnabled() || mc.currentScreen != null) return;
      switch (event.getType()) {
         case PRE:
            if (this.target == null || !this.isValidTarget(this.target.getEntity()) || !this.isBoxInAttackRange(this.target.getBox()) || !this.isBoxInSwingRange(this.target.getBox()) || this.timer.hasTimeElapsed((long)this.switchDelay.getValue())) {
               this.timer.reset();
               ArrayList<EntityLivingBase> targets = new ArrayList<>();
               for (Entity entity : mc.theWorld.loadedEntityList) {
                  if (entity instanceof EntityLivingBase && this.isValidTarget((EntityLivingBase)entity) && this.isInRange((EntityLivingBase)entity))
                     targets.add((EntityLivingBase)entity);
               }
               if (targets.isEmpty()) {
                  this.target = null;
               } else {
                  if (targets.stream().anyMatch(this::isInSwingRange)) targets.removeIf(e -> !this.isInSwingRange(e));
                  if (targets.stream().anyMatch(this::isInAttackRange)) targets.removeIf(e -> !this.isInAttackRange(e));
                  if (targets.stream().anyMatch(this::isPlayerTarget)) targets.removeIf(e -> !this.isPlayerTarget(e));
                  targets.sort((a, b) -> {
                     int s = 0;
                     switch (this.sort.getIndex()) {
                        case 1: s = Float.compare(TeamUtil.getHealthScore(a), TeamUtil.getHealthScore(b)); break;
                        case 2: s = Integer.compare(a.hurtResistantTime, b.hurtResistantTime); break;
                        case 3: s = Float.compare(RotationUtil.angleToEntity(a), RotationUtil.angleToEntity(b)); break;
                     }
                     return s != 0 ? s : Double.compare(RotationUtil.distanceToEntity(a), RotationUtil.distanceToEntity(b));
                  });
                  if (this.mode.getIndex() == 1 && this.hitRegistered) { this.hitRegistered = false; ++this.switchTick; }
                  if (this.mode.getIndex() == 0 || this.switchTick >= targets.size()) this.switchTick = 0;
                  this.target = new AttackData(targets.get(this.switchTick));
               }
            }
            if (this.target != null) this.target = new AttackData(this.target.getEntity());
            break;
         case POST:
            if (this.isPlayerBlocking() && !mc.thePlayer.isBlocking()) {
               mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
            }
            break;
      }
   }

   @EventTarget(4)
   public void onPacket(PacketEvent event) {
      if (!this.isEnabled() || event.isCancelled()) return;
      if (event.getPacket() instanceof C07PacketPlayerDigging) {
         C07PacketPlayerDigging pkt = (C07PacketPlayerDigging)event.getPacket();
         if (pkt.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) this.blockingState = false;
      }
      if (event.getPacket() instanceof C09PacketHeldItemChange) {
         this.blockingState = false;
         if (this.isBlocking) mc.thePlayer.stopUsingItem();
      }
      if (this.debugLog.getIndex() == 1 && this.isAttackAllowed()) {
         if (event.getPacket() instanceof S06PacketUpdateHealth) {
            float diff = ((S06PacketUpdateHealth)event.getPacket()).getHealth() - mc.thePlayer.getHealth();
            if (diff != 0.0F && this.lastTickProcessed != mc.thePlayer.ticksExisted) {
               this.lastTickProcessed = mc.thePlayer.ticksExisted;
               ChatUtil.sendFormatted(String.format("%sHealth: %s&l%s&r (&otick: %d&r)&r", Myau.clientName, diff > 0.0F ? "&a" : "&c", df.format(diff), mc.thePlayer.ticksExisted));
            }
         }
      }
   }

   @EventTarget
   public void onMove(MoveInputEvent event) {
      if (!this.isEnabled()) return;
      if (this.moveFix.getIndex() == 1 && this.rotations.getIndex() != 3 && RotationState.isActived() && RotationState.getPriority() == 1.0F && MoveUtil.isForwardPressed()) {
         MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
      }
      if (this.shouldAutoBlock()) {
         mc.thePlayer.movementInput.sneak = false;
      }
   }

   @EventTarget
   public void onRender(Render3DEvent event) {
      if (!this.isEnabled() || this.target == null || this.showTarget.getIndex() == 0 || !TeamUtil.isEntityLoaded(this.target.getEntity()) || !this.isAttackAllowed()) return;
      Color color;
      switch (this.showTarget.getIndex()) {
         case 1: color = this.target.getEntity().hurtTime > 0 ? new Color(0xFF5555) : new Color(0x55FF55); break;
         case 2: color = ((HUD)Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()); break;
         default: color = new Color(-1);
      }
      RenderUtil.enableRenderState();
      RenderUtil.drawEntityBox(this.target.getEntity(), color.getRed(), color.getGreen(), color.getBlue());
      RenderUtil.disableRenderState();
   }

   @EventTarget
   public void onLeftClick(LeftClickMouseEvent event) {
      if (this.isBlocking || (this.isEnabled() && this.target != null && this.canAttack())) event.setCancelled(true);
   }

   @EventTarget
   public void onRightClick(RightClickMouseEvent event) {
      if (this.isEnabled() && this.isBlocking && this.target != null) event.setCancelled(true);
   }

   @EventTarget
   public void onHitBlock(HitBlockEvent event) {
      if (this.isBlocking || (this.isEnabled() && this.target != null && this.canAttack())) event.setCancelled(true);
   }

   @EventTarget
   public void onCancelUse(CancelUseEvent event) {
      if (this.isBlocking) event.setCancelled(true);
   }

   @Override
   public void onEnabled() {
      this.target = null; this.switchTick = 0; this.hitRegistered = false;
      this.attackDelayMS = 0L; this.blockTick = 0; this.swapped = false;
   }

   @Override
   public void onDisabled() {
      Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
      this.blockingState = false; this.isBlocking = false; this.fakeBlockState = false; this.swapped = false;
   }

   @Override
   public String[] getSuffix() {
      return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getValue().toUpperCase().replace(" ", "_"))};
   }

   public boolean hasTarget() { return this.target != null; }

   private void setNextSlot() {
      PacketUtil.sendPacket(new C09PacketHeldItemChange(this.getNextSlot()));
      this.swapped = true;
   }

   private void setCurrentSlot() {
      if (this.swapped) {
         PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
         this.swapped = false;
      }
   }

   private int getNextSlot() {
      int cur = mc.thePlayer.inventory.currentItem;
      return cur < 8 ? cur + 1 : cur - 1;
   }

   static {
      df = new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));
   }

   public static class AttackData {
      private final EntityLivingBase entity;
      private final AxisAlignedBB box;
      private final double x, y, z;

      public AttackData(EntityLivingBase e) {
         this.entity = e;
         double border = e.getCollisionBorderSize();
         this.box = e.getEntityBoundingBox().expand(border, border, border);
         this.x = e.posX; this.y = e.posY; this.z = e.posZ;
      }

      public EntityLivingBase getEntity() { return entity; }
      public AxisAlignedBB getBox() { return box; }
      public double getX() { return x; }
      public double getY() { return y; }
      public double getZ() { return z; }
   }
}
