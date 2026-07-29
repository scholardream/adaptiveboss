package com.scholardream.adaptiveboss.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.scholardream.adaptiveboss.AdaptiveBossMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * All skill tuning lives here, serialized to {@code config/adaptiveboss.json}.
 * Defaults are written on first launch; edit the file and restart to retune.
 * NOTHING skill-related should be hard-coded in the skill classes.
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("adaptiveboss.json");

    private static ModConfig instance;

    public Charge charge = new Charge();
    public Slam areaSlam = new Slam();
    public Volley projectileVolley = new Volley();
    public Bridge bridge = new Bridge();

    /** Week 3: Python decision bridge (local TCP, NDJSON protocol). */
    public static class Bridge {
        /** Master switch; false = always use the local fallback policy. */
        public boolean enabled = true;
        public String host = "127.0.0.1";
        public int port = 25575;
        /** How long (ms) the game thread waits for a reply before degrading to the fallback policy. */
        public int timeoutMs = 100;
        /** How often (ticks) the policy is asked for a decision. 5 ticks = 0.25 s. */
        public int decisionIntervalTicks = 5;
    }

    public static class Charge {
        public int cooldownTicks = 60;
        public int windupTicks = 15;
        public float damage = 14.0f;
        public double speed = 2.2;
        public double maxRange = 16.0;
        public double minRange = 3.0;
        public int lungeTicks = 15;
        public double hitRadius = 2.2;
    }

    public static class Slam {
        public int cooldownTicks = 100;
        public int windupTicks = 20;
        public float damage = 12.0f;
        public double radius = 5.0;
        public double knockback = 1.5;
    }

    public static class Volley {
        public int cooldownTicks = 80;
        public int windupTicks = 10;
        public int projectileCount = 5;
        public double spreadDegrees = 40.0;
        public double maxRange = 24.0;
        public double projectileSpeed = 1.5;
    }

    public static ModConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static ModConfig load() {
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                ModConfig config = GSON.fromJson(reader, ModConfig.class);
                if (config != null) {
                    AdaptiveBossMod.LOGGER.info("[AdaptiveBoss] loaded config from {}", FILE);
                    return config;
                }
            } catch (Exception e) {
                AdaptiveBossMod.LOGGER.warn("[AdaptiveBoss] failed to read config, using defaults", e);
            }
        }
        ModConfig config = new ModConfig();
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(config, writer);
            AdaptiveBossMod.LOGGER.info("[AdaptiveBoss] wrote default config to {}", FILE);
        } catch (IOException e) {
            AdaptiveBossMod.LOGGER.warn("[AdaptiveBoss] failed to write default config", e);
        }
        return config;
    }
}
