package myau.command.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import myau.command.Command;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestGhostCommand extends Command {
    public TestGhostCommand() {
        super("testghost", "tg");
        setDescription("Test Ghost Intel API");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 1) {
            reply("§cUsage: .testghost <playername>");
            return;
        }

        String playerName = args[0];
        reply("§aTesting Ghost Intel API for: §f" + playerName);

        new Thread(() -> {
            try {
                String url = "https://ghost-intel-bot-production.up.railway.app/api/tags/" + playerName;
                reply("§7URL: §f" + url);

                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setRequestMethod("GET");
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);

                int responseCode = con.getResponseCode();
                reply("§7Response Code: §f" + responseCode);

                if (responseCode == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    String json = response.toString();
                    reply("§7Response: §f" + json);

                    // Parse it
                    JsonObject root = new JsonParser().parse(json).getAsJsonObject();
                    reply("§7Has 'tags' field: §f" + root.has("tags"));

                    if (root.has("tags")) {
                        JsonElement tagsElement = root.get("tags");
                        reply("§7Tags is array: §f" + tagsElement.isJsonArray());

                        if (tagsElement.isJsonArray()) {
                            JsonArray tags = tagsElement.getAsJsonArray();
                            reply("§7Tag count: §f" + tags.size());

                            if (tags.size() > 0) {
                                JsonObject tag = tags.get(0).getAsJsonObject();
                                String type = tag.has("type") ? tag.get("type").getAsString() : "null";
                                String reason = tag.has("reason") ? tag.get("reason").getAsString() : "null";
                                reply("§aTag found! Type: §f" + type + " §aReason: §f" + reason);
                            } else {
                                reply("§cNo tags found for this player");
                            }
                        }
                    }
                } else {
                    reply("§cHTTP Error: " + responseCode);
                }
            } catch (Exception e) {
                reply("§cError: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
}
