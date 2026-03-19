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
        super("testghost", "Test Ghost Intel API");
    }

    @Override
    public void run(String[] args) {
        if (args.length < 1) {
            sendMessage("Usage: .testghost <playername>");
            return;
        }

        String playerName = args[0];
        sendMessage("§aTesting Ghost Intel API for: §f" + playerName);

        new Thread(() -> {
            try {
                String url = "https://ghost-intel-bot-production.up.railway.app/api/tags/" + playerName;
                sendMessage("§7URL: §f" + url);

                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setRequestMethod("GET");
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);

                int responseCode = con.getResponseCode();
                sendMessage("§7Response Code: §f" + responseCode);

                if (responseCode == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    String json = response.toString();
                    sendMessage("§7Response: §f" + json);

                    // Parse it
                    JsonObject root = new JsonParser().parse(json).getAsJsonObject();
                    sendMessage("§7Has 'tags' field: §f" + root.has("tags"));

                    if (root.has("tags")) {
                        JsonElement tagsElement = root.get("tags");
                        sendMessage("§7Tags is array: §f" + tagsElement.isJsonArray());

                        if (tagsElement.isJsonArray()) {
                            JsonArray tags = tagsElement.getAsJsonArray();
                            sendMessage("§7Tag count: §f" + tags.size());

                            if (tags.size() > 0) {
                                JsonObject tag = tags.get(0).getAsJsonObject();
                                String type = tag.has("type") ? tag.get("type").getAsString() : "null";
                                String reason = tag.has("reason") ? tag.get("reason").getAsString() : "null";
                                sendMessage("§aTag found! Type: §f" + type + " §aReason: §f" + reason);
                            } else {
                                sendMessage("§cNo tags found for this player");
                            }
                        }
                    }
                } else {
                    sendMessage("§cHTTP Error: " + responseCode);
                }
            } catch (Exception e) {
                sendMessage("§cError: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
}
