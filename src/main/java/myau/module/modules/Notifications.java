package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.module.Category;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.NumberProperty;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Notifications extends Module {
    public static final String[] COLOR_MODES = new String[]{"Static", "Gradient", "Rainbow"};
    public static final String[] WAVE_AXES = new String[]{"Vertical", "Horizontal"};
    public static final String[] VERTICAL_WAVE_DIRECTIONS = new String[]{"Down", "Up"};
    public static final String[] HORIZONTAL_WAVE_DIRECTIONS = new String[]{"Left", "Right"};

    public final ModeProperty colorMode = new ModeProperty("Color mode", "Static", COLOR_MODES);
    public final BooleanProperty useColorCodes = new BooleanProperty("Use color codes", false);
    public final ModeProperty waveAxis = new ModeProperty("Wave axis", "Vertical", WAVE_AXES);
    public final ModeProperty verticalWaveDirection = new ModeProperty("Wave direction ", "Down", VERTICAL_WAVE_DIRECTIONS);
    public final ModeProperty horizontalWaveDirection = new ModeProperty("Wave direction", "Left", HORIZONTAL_WAVE_DIRECTIONS);
    public final NumberProperty waveSpeed = new NumberProperty("Wave speed", 1.0, 0.1, 5.0, 0.1);
    public final NumberProperty waveLength = new NumberProperty("Wave length", 1.0, 0.5, 5.0, 0.1);
    public final NumberProperty fontSize = new NumberProperty("Scale", 1.0, 0.5, 2.0, 0.1);
    public final NumberProperty animationSpeed = new NumberProperty("Animation speed", 0.1, 0.01, 1.0, 0.01);

    public final BooleanProperty showToggle = new BooleanProperty("Show module toggle", true);
    public final BooleanProperty showState = new BooleanProperty("Show module state", true);
    public final NumberProperty displayTime = new NumberProperty("Display time (s)", 2.0, 0.5, 10.0, 0.5);
    public final IntProperty maxNotifications = new IntProperty("Max notifications", 5, 1, 10, 1);
    public final ModeProperty position = new ModeProperty("Position", "Bottom Right", new String[]{"Bottom Right", "Bottom Left", "Top Right", "Top Left"});

    public final BooleanProperty drawBackground = new BooleanProperty("Draw background", true);
    public final BooleanProperty textShadow = new BooleanProperty("Text shadow", true);
    public final BooleanProperty lowercase = new BooleanProperty("Lowercase", false);

    private final List<Notification> notifications = new ArrayList<>();

    public Notifications() {
        super("Notifications", Category.RENDER);
    }

    public void addNotification(String title, String message, NotificationType type) {
        long durationMs = (long) (displayTime.getValue().doubleValue() * 1000.0);
        notifications.add(new Notification(title, message, type, durationMs));

        int max = maxNotifications.getValue();
        while (notifications.size() > max) {
            notifications.remove(0);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        long now = System.currentTimeMillis();
        Iterator<Notification> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            Notification n = iterator.next();
            if (n.isExpired(now) && n.animationProgress <= 0.01f) {
                iterator.remove();
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null || notifications.isEmpty()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        float scale = fontSize.getValue().floatValue();
        int width = sr.getScaledWidth();
        int height = sr.getScaledHeight();

        float animSpeed = animationSpeed.getValue().floatValue();
        long now = System.currentTimeMillis();

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0f);

        int scaledWidth = (int) (width / scale);
        int scaledHeight = (int) (height / scale);

        int padding = 4;
        int rowHeight = mc.fontRendererObj.FONT_HEIGHT + 6;

        String posMode = position.getValue();
        boolean isTop = posMode.startsWith("Top");
        boolean isRight = posMode.endsWith("Right");

        float startY = isTop ? 10 : scaledHeight - 30;
        float currentY = startY;

        double verticalWaveAccum = 0.0;

        for (int i = 0; i < notifications.size(); i++) {
            Notification n = notifications.get(i);

            boolean expired = n.isExpired(now);
            float targetAnim = expired ? 0.0f : 1.0f;
            n.animationProgress += (targetAnim - n.animationProgress) * animSpeed;

            if (n.animationProgress < 0.02f && expired) continue;

            String displayText = n.message;
            if (lowercase.getValue()) {
                displayText = displayText.toLowerCase();
            }

            int textWidth = mc.fontRendererObj.getStringWidth(displayText);
            int boxWidth = textWidth + padding * 2;
            int boxHeight = rowHeight;

            float targetX = isRight ? (scaledWidth - boxWidth - 10) : 10;
            float offscreenX = isRight ? scaledWidth : -boxWidth;

            float currentX = offscreenX + (targetX - offscreenX) * n.animationProgress;

            double rowCenterX = currentX + boxWidth / 2.0;
            double wavePhase = hudWavePhase(verticalWaveAccum, rowCenterX);
            int color = getHudColor(wavePhase);

            if (hudWaveIsVertical()) {
                verticalWaveAccum += getVerticalWaveStep();
            }

            if (drawBackground.getValue()) {
                Gui.drawRect((int) currentX, (int) currentY, (int) (currentX + boxWidth), (int) (currentY + boxHeight), new Color(0, 0, 0, 150).getRGB());
            }

            // Side bar indicator
            Gui.drawRect((int) currentX, (int) currentY, (int) (currentX + 2), (int) (currentY + boxHeight), color);

            mc.fontRendererObj.drawString(displayText, currentX + padding + 2, currentY + 3, -1, textShadow.getValue());

            if (isTop) {
                currentY += (boxHeight + 3) * n.animationProgress;
            } else {
                currentY -= (boxHeight + 3) * n.animationProgress;
            }
        }

        GlStateManager.popMatrix();
    }

    private boolean hudWaveIsVertical() {
        return waveAxis.getValue().equalsIgnoreCase("Vertical");
    }

    private double hudWavePhase(double verticalAccum, double rowCenterX) {
        return hudWaveIsVertical() ? verticalAccum : rowCenterX * (0.35 / getWaveLengthMultiplier()) * getHorizontalWaveDirectionSign();
    }

    private double getVerticalWaveStep() {
        return 12.0 / getWaveLengthMultiplier() * getVerticalWaveDirectionSign();
    }

    private int getVerticalWaveDirectionSign() {
        return verticalWaveDirection.getValue().equalsIgnoreCase("Up") ? 1 : -1;
    }

    private int getHorizontalWaveDirectionSign() {
        return horizontalWaveDirection.getValue().equalsIgnoreCase("Right") ? 1 : -1;
    }

    public int getHudColor(double gradientOffset) {
        String mode = colorMode.getValue();
        if ("Rainbow".equalsIgnoreCase(mode)) {
            return getRainbowWaveColor(gradientOffset);
        } else if ("Gradient".equalsIgnoreCase(mode)) {
            return getGradientWaveColor(Color.WHITE, new Color(85, 85, 255), gradientOffset);
        } else {
            return Color.WHITE.getRGB();
        }
    }

    private int getGradientWaveColor(Color c1, Color c2, double gradientOffset) {
        double animProgress = (Math.sin(getAnimatedWaveAngle(gradientOffset)) + 1.0) * 0.5;
        int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * animProgress);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * animProgress);
        int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * animProgress);
        return new Color(r, g, b).getRGB();
    }

    private int getRainbowWaveColor(double gradientOffset) {
        double hue = getAnimatedWaveAngle(gradientOffset) / (Math.PI * 2D);
        hue -= Math.floor(hue);
        return Color.getHSBColor((float) hue, 1.0F, 1.0F).getRGB();
    }

    private double getAnimatedWaveAngle(double gradientOffset) {
        return (double) System.currentTimeMillis() / 7500.0F * (Math.PI * 2D) * getWaveSpeedMultiplier() + gradientOffset * 0.12;
    }

    private double getWaveSpeedMultiplier() {
        return Math.max(0.1, waveSpeed.getValue().doubleValue());
    }

    private double getWaveLengthMultiplier() {
        return Math.max(0.5, waveLength.getValue().doubleValue());
    }

    public enum NotificationType {
        INFO, WARNING, ERROR
    }

    public static class Notification {
        public final String title;
        public final String message;
        public final NotificationType type;
        public final long creationTime;
        public final long duration;
        public float animationProgress = 0.0f;

        public Notification(String title, String message, NotificationType type, long duration) {
            this.title = title;
            this.message = message;
            this.type = type;
            this.duration = duration;
            this.creationTime = System.currentTimeMillis();
        }

        public boolean isExpired(long now) {
            return now - creationTime > duration;
        }
    }
}
