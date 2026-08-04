package myau.module;

import myau.property.Property;
import myau.property.properties.BooleanProperty;
import myau.module.modules.Notifications;
import myau.module.ModuleManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    protected static final Minecraft mc = Minecraft.getMinecraft();

    private final String name;
    private final String nameInHud;
    private final Category category;
    private final List<Property<?>> properties = new ArrayList<>();

    public int key = 0;
    public final BooleanProperty enabled = new BooleanProperty("Enabled", false);
    public final BooleanProperty hidden = new BooleanProperty("Hidden", false);

    // Animation progress for HUD rendering
    public float hudAnimation = 0.0f;

    public Module(String name, Category category) {
        this(name, name, category);
    }

    public Module(String name, String nameInHud, Category category) {
        this.name = name;
        this.nameInHud = nameInHud;
        this.category = category;
        this.registerProperties(enabled, hidden);
    }

    public String getName() {
        return name;
    }

    public String getNameInHud() {
        return nameInHud;
    }

    public Category getCategory() {
        return category;
    }

    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public String getInfo() {
        return "";
    }

    public void getInfoUpdate() {
    }

    public boolean isEnabled() {
        return enabled.getValue();
    }

    public void setEnabled(boolean state) {
        if (this.enabled.getValue() != state) {
            this.enabled.setValue(state);
            if (state) {
                onEnable();
            } else {
                onDisable();
            }
            sendToggleNotification(state);
        }
    }

    public void toggle() {
        setEnabled(!isEnabled());
    }

    public boolean isHidden() {
        return hidden.getValue();
    }

    public void setHidden(boolean hidden) {
        this.hidden.setValue(hidden);
    }

    public List<Property<?>> getProperties() {
        return properties;
    }

    protected void registerProperties(Property<?>... props) {
        for (Property<?> prop : props) {
            properties.add(prop);
        }
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    private void sendToggleNotification(boolean state) {
        Notifications notifModule = ModuleManager.getModule(Notifications.class);
        if (notifModule != null && notifModule.isEnabled()) {
            String title = this.getName();
            String message = title + (state ? " Enabled" : " Disabled");
            notifModule.addNotification(title, message, Notifications.NotificationType.INFO);
        }
    }
}
