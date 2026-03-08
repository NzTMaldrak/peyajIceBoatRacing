package peyaj.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Handles async file I/O operations.
 */
public class AsyncIO {

    private static final Executor IO_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "IceBoatRacing-IO");
        t.setDaemon(true);
        return t;
    });

    /**
     * Saves a YAML configuration asynchronously.
     */
    public static CompletableFuture<Void> saveConfigAsync(FileConfiguration config, File file, JavaPlugin plugin) {
        return CompletableFuture.runAsync(() -> {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save config async: " + e.getMessage());
            }
        }, IO_EXECUTOR);
    }

    /**
     * Loads a YAML configuration asynchronously.
     */
    public static CompletableFuture<FileConfiguration> loadConfigAsync(File file) {
        return CompletableFuture.supplyAsync(() -> {
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    return new YamlConfiguration();
                }
            }
            return YamlConfiguration.loadConfiguration(file);
        }, IO_EXECUTOR);
    }

    /**
     * Runs an IO operation asynchronously.
     */
    public static CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, IO_EXECUTOR);
    }

    /**
     * Runs an IO operation asynchronously and returns a result.
     */
    public static <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, IO_EXECUTOR);
    }
}
