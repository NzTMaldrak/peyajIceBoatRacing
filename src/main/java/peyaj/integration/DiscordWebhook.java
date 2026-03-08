package peyaj.integration;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles Discord webhook integration with rich embeds.
 */
public class DiscordWebhook {

    private final JavaPlugin plugin;
    private final HttpClient httpClient;

    public DiscordWebhook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Sends race results to the Discord webhook.
     */
    public CompletableFuture<Void> sendRaceResults(String webhookUrl, String arenaName,
            List<UUID> finishOrder, Map<UUID, String> finishTimes) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                StringBuilder description = new StringBuilder();
                String[] emojis = { "🥇", "🥈", "🥉" };
                String[] colors = { "FFD700", "C0C0C0", "CD7F32" };

                int rank = 1;
                String winnerName = null;
                String winnerUuid = null;

                for (UUID uuid : finishOrder) {
                    if (rank > 10)
                        break;

                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    String name = (op.getName() != null) ? op.getName() : "Unknown";
                    String time = finishTimes.getOrDefault(uuid, "DNF");

                    if (rank == 1) {
                        winnerName = name;
                        winnerUuid = uuid.toString().replace("-", "");
                    }

                    String medal = (rank <= 3) ? emojis[rank - 1] + " " : "**#" + rank + "** ";
                    description.append(medal).append("**").append(name).append("** — `").append(time).append("`\\n");
                    rank++;
                }

                // Build rich embed
                StringBuilder json = new StringBuilder();
                json.append("{\"embeds\": [{");
                json.append("\"title\": \"🏆 Race Complete: ").append(escapeJson(arenaName)).append("\",");
                json.append("\"color\": 5814783,"); // Ice blue color
                json.append("\"description\": \"").append(description).append("\",");

                // Add fields
                json.append("\"fields\": [");
                json.append("{\"name\": \"🏁 Arena\", \"value\": \"").append(escapeJson(arenaName))
                        .append("\", \"inline\": true},");
                json.append("{\"name\": \"👥 Racers\", \"value\": \"").append(finishOrder.size())
                        .append("\", \"inline\": true}");
                json.append("],");

                // Add winner thumbnail
                if (winnerUuid != null) {
                    json.append("\"thumbnail\": {\"url\": \"https://crafatar.com/avatars/").append(winnerUuid)
                            .append("?overlay=true&size=128\"},");
                }

                // Footer and timestamp
                json.append(
                        "\"footer\": {\"text\": \"IceBoatRacing\", \"icon_url\": \"https://i.imgur.com/Q8tQdY1.png\"},");
                json.append("\"timestamp\": \"").append(Instant.now().toString()).append("\"");
                json.append("}]}");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                        .build();

                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            } catch (Exception e) {
                plugin.getLogger().warning("Discord webhook failed: " + e.getMessage());
            }
        });
    }

    /**
     * Sends a race start notification.
     */
    public CompletableFuture<Void> sendRaceStart(String webhookUrl, String arenaName, int playerCount) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                String json = "{\"embeds\": [{" +
                        "\"title\": \"🚦 Race Starting!\"," +
                        "\"description\": \"A race is beginning on **" + escapeJson(arenaName) + "**\"," +
                        "\"color\": 3066993," + // Green
                        "\"fields\": [{\"name\": \"👥 Players\", \"value\": \"" + playerCount
                        + "\", \"inline\": true}]," +
                        "\"footer\": {\"text\": \"IceBoatRacing\"}," +
                        "\"timestamp\": \"" + Instant.now().toString() + "\"" +
                        "}]}";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            } catch (Exception e) {
                plugin.getLogger().warning("Discord webhook failed: " + e.getMessage());
            }
        });
    }

    /**
     * Sends a new record notification.
     */
    public CompletableFuture<Void> sendNewRecord(String webhookUrl, String arenaName,
            String playerName, String time, boolean isServerRecord) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                String title = isServerRecord ? "🌟 NEW SERVER RECORD!" : "⏱️ New Personal Best!";
                int color = isServerRecord ? 16766720 : 3447003; // Gold or Blue

                String json = "{\"embeds\": [{" +
                        "\"title\": \"" + title + "\"," +
                        "\"description\": \"**" + escapeJson(playerName) + "** set a new record on **"
                        + escapeJson(arenaName) + "**\"," +
                        "\"color\": " + color + "," +
                        "\"fields\": [{\"name\": \"⏱️ Time\", \"value\": \"`" + escapeJson(time)
                        + "`\", \"inline\": true}]," +
                        "\"footer\": {\"text\": \"IceBoatRacing\"}," +
                        "\"timestamp\": \"" + Instant.now().toString() + "\"" +
                        "}]}";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            } catch (Exception e) {
                plugin.getLogger().warning("Discord webhook failed: " + e.getMessage());
            }
        });
    }

    private String escapeJson(String input) {
        if (input == null)
            return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
