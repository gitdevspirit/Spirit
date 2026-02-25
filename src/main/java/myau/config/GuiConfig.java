package myau.config;

import com.google.gson.*;
import net.minecraft.client.Minecraft;

import java.io.*;

public class GuiConfig {

    private static final File FILE = new File("./config/Myau/gui.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Default position
    public static int guiX = 130;
    public static int guiY = 20;

    public static void save() {
        try {
            FILE.getParentFile().mkdirs();

            JsonObject obj = new JsonObject();
            obj.addProperty("x", guiX);
            obj.addProperty("y", guiY);

            PrintWriter writer = new PrintWriter(new FileWriter(FILE));
            writer.println(GSON.toJson(obj));
            writer.close();

        } catch (IOException e) {
            System.err.println("[GuiConfig] Failed to save: " + e.getMessage());
        }
    }

    public static void load() {
        try {
            if (!FILE.exists()) {
                save(); // create with defaults
                return;
            }

            JsonElement parsed = new JsonParser().parse(new BufferedReader(new FileReader(FILE)));
            if (parsed == null || !parsed.isJsonObject()) return;

            JsonObject obj = parsed.getAsJsonObject();

            if (obj.has("x")) guiX = obj.get("x").getAsInt();
            if (obj.has("y")) guiY = obj.get("y").getAsInt();

        } catch (Exception e) {
            System.err.println("[GuiConfig] Failed to load: " + e.getMessage());
        }
    }
}
