package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.Render2DEvent;
import myau.events.Render3DEvent;
import myau.events.UpdateEvent;
import myau.module.BooleanSetting;
import myau.module.Module;
import myau.module.SliderSetting;
import myau.module.DropdownSetting;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Quality of Life - Parent module for visual/UX improvements
 * Submodules: Block Overlay, Keystrokes, CPS Counter
 */
public class QualityOfLife extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    
    // ═══════════════════════════════════════════════════════════════════
    // BLOCK OVERLAY - Lunar-style block highlighting
    // ═══════════════════════════════════════════════════════════════════
    public final BooleanSetting blockOverlay       = register(new BooleanSetting("Block Overlay", true));
    public final BooleanSetting blockOutline       = register(new BooleanSetting("  Show Outline", true));
    public final BooleanSetting blockFill          = register(new BooleanSetting("  Show Fill", true));
    public final SliderSetting  blockOutlineWidth  = register(new SliderSetting("  Outline Width", 2.0, 1.0, 5.0, 0.5));
    public final SliderSetting  blockOutlineRed    = register(new SliderSetting("  Outline Red", 0, 0, 255, 1));
    public final SliderSetting  blockOutlineGreen  = register(new SliderSetting("  Outline Green", 0, 0, 255, 1));
    public final SliderSetting  blockOutlineBlue   = register(new SliderSetting("  Outline Blue", 0, 0, 255, 1));
    public final SliderSetting  blockOutlineAlpha  = register(new SliderSetting("  Outline Alpha", 255, 0, 255, 1));
    public final SliderSetting  blockFillRed       = register(new SliderSetting("  Fill Red", 0, 0, 255, 1));
    public final SliderSetting  blockFillGreen     = register(new SliderSetting("  Fill Green", 0, 0, 255, 1));
    public final SliderSetting  blockFillBlue      = register(new SliderSetting("  Fill Blue", 0, 0, 255, 1));
    public final SliderSetting  blockFillAlpha     = register(new SliderSetting("  Fill Alpha", 50, 0, 255, 1));
    
    // ═══════════════════════════════════════════════════════════════════
    // KEYSTROKES - Visual key press display
    // ═══════════════════════════════════════════════════════════════════
    public final BooleanSetting keystrokes         = register(new BooleanSetting("Keystrokes", true));
    public final SliderSetting  keystrokesX        = register(new SliderSetting("  X Position", 10, 0, 1920, 1));
    public final SliderSetting  keystrokesY        = register(new SliderSetting("  Y Position", 10, 0, 1080, 1));
    public final SliderSetting  keystrokesScale    = register(new SliderSetting("  Scale", 1.0, 0.5, 2.0, 0.1));
    public final BooleanSetting keystrokesShowCPS  = register(new BooleanSetting("  Show CPS", true));
    public final DropdownSetting keystrokesMode    = register(new DropdownSetting("  Mode", 0, "WASD", "WASD+SPACE", "WASD+MOUSE"));
    
    // ═══════════════════════════════════════════════════════════════════
    // CPS COUNTER - Clicks per second display
    // ═══════════════════════════════════════════════════════════════════
    public final BooleanSetting cpsCounter         = register(new BooleanSetting("CPS Counter", true));
    public final SliderSetting  cpsX               = register(new SliderSetting("  X Position", 10, 0, 1920, 1));
    public final SliderSetting  cpsY               = register(new SliderSetting("  Y Position", 100, 0, 1080, 1));
    public final SliderSetting  cpsScale           = register(new SliderSetting("  Scale", 1.0, 0.5, 2.0, 0.1));
    public final BooleanSetting cpsShowLMB         = register(new BooleanSetting("  Show LMB", true));
    public final BooleanSetting cpsShowRMB         = register(new BooleanSetting("  Show RMB", true));
    
    // Click tracking
    private final List<Long> leftClicks = new ArrayList<>();
    private final List<Long> rightClicks = new ArrayList<>();
    
    public QualityOfLife() {
        super("QualityOfLife", false);
    }
    
    @Override
    public void onEnabled() {
        leftClicks.clear();
        rightClicks.clear();
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // BLOCK OVERLAY RENDERING
    // ═══════════════════════════════════════════════════════════════════
    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || !blockOverlay.getValue()) return;
        if (mc.objectMouseOver == null) return;
        if (mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        
        BlockPos pos = mc.objectMouseOver.getBlockPos();
        if (pos == null) return;
        
        double x = pos.getX() - mc.getRenderManager().viewerPosX;
        double y = pos.getY() - mc.getRenderManager().viewerPosY;
        double z = pos.getZ() - mc.getRenderManager().viewerPosZ;
        
        AxisAlignedBB bb = new AxisAlignedBB(x, y, z, x + 1, y + 1, z + 1);
        
        // Get colors
        Color outlineColor = new Color(
            (int) blockOutlineRed.getValue(),
            (int) blockOutlineGreen.getValue(),
            (int) blockOutlineBlue.getValue(),
            (int) blockOutlineAlpha.getValue()
        );
        
        Color fillColor = new Color(
            (int) blockFillRed.getValue(),
            (int) blockFillGreen.getValue(),
            (int) blockFillBlue.getValue(),
            (int) blockFillAlpha.getValue()
        );
        
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        
        // Draw fill
        if (blockFill.getValue()) {
            drawFilledBox(bb, fillColor);
        }
        
        // Draw outline
        if (blockOutline.getValue()) {
            GL11.glLineWidth((float) blockOutlineWidth.getValue());
            drawOutlinedBox(bb, outlineColor);
        }
        
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
    
    private void drawFilledBox(AxisAlignedBB bb, Color color) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        
        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float a = color.getAlpha() / 255f;
        
        // Bottom
        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        
        // Top
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        
        // Front
        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        
        // Back
        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        
        // Left
        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        
        // Right
        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        
        tessellator.draw();
    }
    
    private void drawOutlinedBox(AxisAlignedBB bb, Color color) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        
        worldRenderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        float a = color.getAlpha() / 255f;
        
        // Bottom edges
        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        
        // Top edges
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        
        // Vertical edges
        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        
        tessellator.draw();
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // CPS TRACKING
    // ═══════════════════════════════════════════════════════════════════
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;
        
        long now = System.currentTimeMillis();
        
        // Track left clicks
        if (Mouse.isButtonDown(0)) {
            if (leftClicks.isEmpty() || now - leftClicks.get(leftClicks.size() - 1) > 50) {
                leftClicks.add(now);
            }
        }
        
        // Track right clicks
        if (Mouse.isButtonDown(1)) {
            if (rightClicks.isEmpty() || now - rightClicks.get(rightClicks.size() - 1) > 50) {
                rightClicks.add(now);
            }
        }
        
        // Remove clicks older than 1 second
        leftClicks.removeIf(time -> now - time > 1000);
        rightClicks.removeIf(time -> now - time > 1000);
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // HUD RENDERING - Keystrokes & CPS
    // ═══════════════════════════════════════════════════════════════════
    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled()) return;
        
        if (keystrokes.getValue()) {
            renderKeystrokes();
        }
        
        if (cpsCounter.getValue()) {
            renderCPS();
        }
    }
    
    private void renderKeystrokes() {
        float scale = (float) keystrokesScale.getValue();
        int x = (int) keystrokesX.getValue();
        int y = (int) keystrokesY.getValue();
        
        int keySize = 25;
        int gap = 2;
        
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0f);
        
        int scaledX = (int) (x / scale);
        int scaledY = (int) (y / scale);
        
        int mode = keystrokesMode.getIndex();
        
        // WASD keys
        drawKey("W", scaledX + keySize + gap, scaledY, keySize, mc.gameSettings.keyBindForward.isKeyDown());
        drawKey("A", scaledX, scaledY + keySize + gap, keySize, mc.gameSettings.keyBindLeft.isKeyDown());
        drawKey("S", scaledX + keySize + gap, scaledY + keySize + gap, keySize, mc.gameSettings.keyBindBack.isKeyDown());
        drawKey("D", scaledX + (keySize + gap) * 2, scaledY + keySize + gap, keySize, mc.gameSettings.keyBindRight.isKeyDown());
        
        // Spacebar for WASD+SPACE mode
        if (mode == 1) {
            int spaceY = scaledY + (keySize + gap) * 2;
            drawKey("---", scaledX, spaceY, keySize * 3 + gap * 2, mc.gameSettings.keyBindJump.isKeyDown());
        }
        
        // Mouse buttons for WASD+MOUSE mode
        if (mode == 2) {
            int mouseY = scaledY + (keySize + gap) * 2;
            drawKey("LMB", scaledX, mouseY, (keySize * 3 + gap * 2) / 2 - gap / 2, Mouse.isButtonDown(0));
            drawKey("RMB", scaledX + (keySize * 3 + gap * 2) / 2 + gap / 2, mouseY, (keySize * 3 + gap * 2) / 2 - gap / 2, Mouse.isButtonDown(1));
            
            // Show CPS under mouse buttons
            if (keystrokesShowCPS.getValue()) {
                String cpsText = leftClicks.size() + " | " + rightClicks.size() + " CPS";
                int cpsW = mc.fontRendererObj.getStringWidth(cpsText);
                mc.fontRendererObj.drawStringWithShadow(cpsText, 
                    scaledX + (keySize * 3 + gap * 2) / 2 - cpsW / 2, 
                    mouseY + keySize + 5, 
                    0xFFFFFFFF);
            }
        }
        
        GlStateManager.popMatrix();
    }
    
    private void drawKey(String label, int x, int y, int width, boolean pressed) {
        int height = 25;
        int bgColor = pressed ? 0x88FFFFFF : 0x88000000;
        int borderColor = pressed ? 0xFFFFFFFF : 0xFF555555;
        
        // Background
        drawRect(x, y, width, height, bgColor);
        
        // Border
        drawHollowRect(x, y, width, height, 1, borderColor);
        
        // Label
        int labelW = mc.fontRendererObj.getStringWidth(label);
        mc.fontRendererObj.drawStringWithShadow(label, 
            x + width / 2 - labelW / 2, 
            y + height / 2 - 4, 
            0xFFFFFFFF);
    }
    
    private void renderCPS() {
        float scale = (float) cpsScale.getValue();
        int x = (int) cpsX.getValue();
        int y = (int) cpsY.getValue();
        
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0f);
        
        int scaledX = (int) (x / scale);
        int scaledY = (int) (y / scale);
        
        int leftCPS = leftClicks.size();
        int rightCPS = rightClicks.size();
        
        if (cpsShowLMB.getValue() && cpsShowRMB.getValue()) {
            String text = leftCPS + " | " + rightCPS + " CPS";
            mc.fontRendererObj.drawStringWithShadow(text, scaledX, scaledY, 0xFFFFFFFF);
        } else if (cpsShowLMB.getValue()) {
            String text = leftCPS + " CPS";
            mc.fontRendererObj.drawStringWithShadow(text, scaledX, scaledY, 0xFFFFFFFF);
        } else if (cpsShowRMB.getValue()) {
            String text = rightCPS + " CPS";
            mc.fontRendererObj.drawStringWithShadow(text, scaledX, scaledY, 0xFFFFFFFF);
        }
        
        GlStateManager.popMatrix();
    }
    
    private void drawRect(int x, int y, int width, int height, int color) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        
        float a = (color >> 24 & 0xFF) / 255.0F;
        float r = (color >> 16 & 0xFF) / 255.0F;
        float g = (color >> 8 & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        
        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        worldRenderer.pos(x, y + height, 0).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + width, y + height, 0).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + width, y, 0).color(r, g, b, a).endVertex();
        worldRenderer.pos(x, y, 0).color(r, g, b, a).endVertex();
        tessellator.draw();
        
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }
    
    private void drawHollowRect(int x, int y, int width, int height, int lineWidth, int color) {
        drawRect(x, y, width, lineWidth, color); // Top
        drawRect(x, y + height - lineWidth, width, lineWidth, color); // Bottom
        drawRect(x, y, lineWidth, height, color); // Left
        drawRect(x + width - lineWidth, y, lineWidth, height, color); // Right
    }
}
