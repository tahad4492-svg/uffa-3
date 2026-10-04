package com.mrtahadarvish.unstableffa;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Builds player heads with the real skin of a Minecraft username.
 * The skin is looked up directly at Mojang (with a backup service) so the server
 * can tell the admin exactly what went wrong, and every skin found is cached in
 * skins.yml so it works again later even if Mojang is slow or rate limiting.
 */
final class HeadUtil {

    private record Skin(UUID id, String name, String value, String signature) { }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final Map<String, Skin> CACHE = new HashMap<>();
    private static File cacheFile;

    private HeadUtil() { }

    /** Loads skins.yml. Called once from onEnable. */
    static void init(UnstableFFA plugin) {
        cacheFile = new File(plugin.getDataFolder(), "skins.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(cacheFile);
        ConfigurationSection root = yaml.getConfigurationSection("skins");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null || s.getString("value") == null || s.getString("uuid") == null) {
                continue;
            }
            try {
                CACHE.put(key, new Skin(UUID.fromString(s.getString("uuid")), s.getString("name", key),
                        s.getString("value"), s.getString("signature")));
            } catch (IllegalArgumentException ignored) {
                // skip a broken cache entry
            }
        }
    }

    static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}");
    }

    /**
     * Looks the skin up in the background, then calls {@code onSuccess} (or {@code onFail}
     * with a readable reason) back on the main server thread.
     */
    static void fetch(UnstableFFA plugin, String name, Consumer<ItemStack> onSuccess, Consumer<String> onFail) {
        String key = name.toLowerCase(Locale.ROOT);
        Skin cached = CACHE.get(key);
        if (cached != null) {
            onSuccess.accept(head(cached));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            StringBuilder problems = new StringBuilder();
            Skin skin = lookupMojang(name, problems);
            if (skin == null) {
                skin = lookupBackup(name, problems);
            }
            final Skin found = skin;
            final String why = problems.toString().trim();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (found == null) {
                    plugin.getLogger().warning("Could not fetch the skin of " + name + ": " + why);
                    onFail.accept(why.isEmpty() ? "unknown error" : why);
                    return;
                }
                CACHE.put(key, found);
                saveCache();
                onSuccess.accept(head(found));
            });
        });
    }

    // ---- lookups (run off the main thread) --------------------------------------------------

    private static Skin lookupMojang(String name, StringBuilder problems) {
        try {
            String id = null;
            for (String url : new String[]{
                    "https://api.minecraftservices.com/minecraft/profile/lookup/name/" + name,
                    "https://api.mojang.com/users/profiles/minecraft/" + name}) {
                HttpResponse<String> r = get(url);
                if (r.statusCode() == 200) {
                    id = JsonParser.parseString(r.body()).getAsJsonObject().get("id").getAsString();
                    break;
                }
                if (r.statusCode() == 204 || r.statusCode() == 404) {
                    problems.append("No Minecraft account is named '").append(name).append("'. ");
                    return null;
                }
                problems.append("Mojang answered ").append(r.statusCode()).append(" (")
                        .append(r.statusCode() == 429 ? "rate limited, wait a minute" : "error").append("). ");
            }
            if (id == null) {
                return null;
            }
            HttpResponse<String> r = get("https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false");
            if (r.statusCode() != 200) {
                problems.append("Mojang skin server answered ").append(r.statusCode()).append(". ");
                return null;
            }
            JsonObject profile = JsonParser.parseString(r.body()).getAsJsonObject();
            JsonArray props = profile.getAsJsonArray("properties");
            if (props != null) {
                for (JsonElement e : props) {
                    JsonObject o = e.getAsJsonObject();
                    if ("textures".equals(o.get("name").getAsString())) {
                        return new Skin(parseUuid(id), profile.get("name").getAsString(), o.get("value").getAsString(),
                                o.has("signature") ? o.get("signature").getAsString() : null);
                    }
                }
            }
            problems.append("That account has no skin data. ");
        } catch (Exception ex) {
            problems.append("Could not reach Mojang (").append(ex.getClass().getSimpleName()).append(": ")
                    .append(ex.getMessage()).append("). ");
        }
        return null;
    }

    private static Skin lookupBackup(String name, StringBuilder problems) {
        try {
            HttpResponse<String> r = get("https://api.ashcon.app/mojang/v2/user/" + name);
            if (r.statusCode() != 200) {
                problems.append("Backup skin service answered ").append(r.statusCode()).append(". ");
                return null;
            }
            JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
            JsonObject raw = root.getAsJsonObject("textures").getAsJsonObject("raw");
            return new Skin(UUID.fromString(root.get("uuid").getAsString()), root.get("username").getAsString(),
                    raw.get("value").getAsString(), raw.has("signature") ? raw.get("signature").getAsString() : null);
        } catch (Exception ex) {
            problems.append("Backup skin service failed (").append(ex.getClass().getSimpleName()).append("). ");
            return null;
        }
    }

    private static HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                .header("User-Agent", "UnstableFFA").GET().build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static UUID parseUuid(String undashed) {
        if (undashed.contains("-")) {
            return UUID.fromString(undashed);
        }
        return UUID.fromString(undashed.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
    }

    // ---- cache ------------------------------------------------------------------------------------------

    private static void saveCache() {
        if (cacheFile == null) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Skin> e : CACHE.entrySet()) {
            String base = "skins." + e.getKey();
            yaml.set(base + ".uuid", e.getValue().id().toString());
            yaml.set(base + ".name", e.getValue().name());
            yaml.set(base + ".value", e.getValue().value());
            yaml.set(base + ".signature", e.getValue().signature());
        }
        try {
            cacheFile.getParentFile().mkdirs();
            yaml.save(cacheFile);
        } catch (IOException ignored) {
            // the cache is only a convenience
        }
    }

    // ---- building heads ---------------------------------------------------------------------------------------

    private static ItemStack head(Skin skin) {
        PlayerProfile profile = Bukkit.createProfile(skin.id(), skin.name());
        profile.setProperty(skin.signature() == null
                ? new ProfileProperty("textures", skin.value())
                : new ProfileProperty("textures", skin.value(), skin.signature()));
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    /** A head built from a raw skin texture value (the base64 "Value" from minecraft-heads.com or similar). */
    static ItemStack fromTexture(String base64Value) {
        return head(new Skin(UUID.randomUUID(), "head", base64Value, null));
    }

    /** Plain "owner = this name" head. Last resort when no skin could be fetched. */
    static ItemStack byOwnerName(String name) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(name));
        head.setItemMeta(meta);
        return head;
    }
}
