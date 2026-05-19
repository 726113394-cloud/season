package io.Sriptirc_wp_1258.seasonalplugin.config;

import io.Sriptirc_wp_1258.seasonalplugin.SeasonalPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * 配置管理 - 读取 config.yml 并提供类型安全的配置访问
 */
public class ConfigManager {

    private final SeasonalPlugin plugin;
    private FileConfiguration config;

    // === 季节更替 ===
    private boolean useRealTime;
    private int realTimeDaysPerSeason;
    private int mcDaysPerSeason;

    // === 天气 ===
    private boolean weatherEnabled;
    private double springRainChance;
    private double summerRainChance;
    private double autumnRainChance;
    private double winterRainChance;
    private double springStormChance;
    private double summerStormChance;
    private double autumnStormChance;
    private double winterStormChance;

    // === 作物 ===
    private boolean cropGrowthEnabled;
    private double springGrowthMultiplier;
    private double summerGrowthMultiplier;
    private double autumnGrowthMultiplier;
    private double winterGrowthMultiplier;

    // === 秋季翻倍 ===
    private boolean harvestBoostEnabled;
    private double harvestBoostMultiplier;

    // === 钓鱼翻倍 ===
    private boolean fishingBoostEnabled;
    private double fishingBoostMultiplier;
    private boolean fishingSpeedBoost;
    private int fishingSpeedBoostTicks;

    // === 冻伤 ===
    private boolean freezeEnabled;
    private int freezeIntervalTicks;
    private double freezeDamage;
    private int freezeSlowAmplifier;
    private int freezeSlowDuration;

    // === Lore ===
    private boolean loreEnabled;

    // === 树叶粒子 ===
    private boolean leafParticlesEnabled;

    public ConfigManager(SeasonalPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // 版本检查
        int configVersion = config.getInt("ScriptIrc-config-version", 0);
        if (configVersion < 1) {
            plugin.getLogger().warning("配置文件版本过旧，建议删除后重新生成！");
        }

        // === 季节更替 ===
        useRealTime = config.getBoolean("season-cycle.use-real-time", true);
        realTimeDaysPerSeason = config.getInt("season-cycle.real-time-days-per-season", 7);
        mcDaysPerSeason = config.getInt("season-cycle.mc-days-per-season", 30);

        // === 天气 ===
        weatherEnabled = config.getBoolean("weather.enabled", true);
        springRainChance = config.getDouble("weather.spring-rain-chance", 0.7);
        summerRainChance = config.getDouble("weather.summer-rain-chance", 0.15);
        autumnRainChance = config.getDouble("weather.autumn-rain-chance", 0.5);
        winterRainChance = config.getDouble("weather.winter-rain-chance", 0.4);
        springStormChance = config.getDouble("weather.spring-storm-chance", 0.3);
        summerStormChance = config.getDouble("weather.summer-storm-chance", 0.05);
        autumnStormChance = config.getDouble("weather.autumn-storm-chance", 0.2);
        winterStormChance = config.getDouble("weather.winter-storm-chance", 0.15);

        // === 作物 ===
        cropGrowthEnabled = config.getBoolean("crop-growth.enabled", true);
        springGrowthMultiplier = config.getDouble("crop-growth.spring-multiplier", 1.5);
        summerGrowthMultiplier = config.getDouble("crop-growth.summer-multiplier", 1.2);
        autumnGrowthMultiplier = config.getDouble("crop-growth.autumn-multiplier", 1.0);
        winterGrowthMultiplier = config.getDouble("crop-growth.winter-multiplier", 0.3);

        // === 秋季翻倍 ===
        harvestBoostEnabled = config.getBoolean("harvest-boost.enabled", true);
        harvestBoostMultiplier = config.getDouble("harvest-boost.multiplier", 2.0);

        // === 钓鱼翻倍 ===
        fishingBoostEnabled = config.getBoolean("fishing-boost.enabled", true);
        fishingBoostMultiplier = config.getDouble("fishing-boost.multiplier", 2.0);
        fishingSpeedBoost = config.getBoolean("fishing-boost.speed-boost", true);
        fishingSpeedBoostTicks = config.getInt("fishing-boost.speed-boost-ticks", 60);

        // === 冻伤 ===
        freezeEnabled = config.getBoolean("freeze.enabled", true);
        freezeIntervalTicks = config.getInt("freeze.interval-ticks", 100);
        freezeDamage = config.getDouble("freeze.damage", 1.0);
        freezeSlowAmplifier = config.getInt("freeze.slow-amplifier", 0);
        freezeSlowDuration = config.getInt("freeze.slow-duration", 100);

        // === Lore ===
        loreEnabled = config.getBoolean("lore.enabled", true);

        // === 树叶粒子 ===
        leafParticlesEnabled = config.getBoolean("leaf-particles.enabled", true);
    }

    // === Getters ===

    public boolean isUseRealTime() { return useRealTime; }
    public int getRealTimeDaysPerSeason() { return realTimeDaysPerSeason; }
    public int getMcDaysPerSeason() { return mcDaysPerSeason; }

    public boolean isWeatherEnabled() { return weatherEnabled; }
    public double getRainChanceForSeason(String season) {
        return switch (season.toLowerCase()) {
            case "spring" -> springRainChance;
            case "summer" -> summerRainChance;
            case "autumn" -> autumnRainChance;
            case "winter" -> winterRainChance;
            default -> 0.3;
        };
    }
    public double getStormChanceForSeason(String season) {
        return switch (season.toLowerCase()) {
            case "spring" -> springStormChance;
            case "summer" -> summerStormChance;
            case "autumn" -> autumnStormChance;
            case "winter" -> winterStormChance;
            default -> 0.1;
        };
    }

    public boolean isCropGrowthEnabled() { return cropGrowthEnabled; }
    public double getGrowthMultiplierForSeason(String season) {
        return switch (season.toLowerCase()) {
            case "spring" -> springGrowthMultiplier;
            case "summer" -> summerGrowthMultiplier;
            case "autumn" -> autumnGrowthMultiplier;
            case "winter" -> winterGrowthMultiplier;
            default -> 1.0;
        };
    }

    public boolean isHarvestBoostEnabled() { return harvestBoostEnabled; }
    public double getHarvestBoostMultiplier() { return harvestBoostMultiplier; }

    public boolean isFishingBoostEnabled() { return fishingBoostEnabled; }
    public double getFishingBoostMultiplier() { return fishingBoostMultiplier; }
    public boolean isFishingSpeedBoost() { return fishingSpeedBoost; }
    public int getFishingSpeedBoostTicks() { return fishingSpeedBoostTicks; }

    public boolean isFreezeEnabled() { return freezeEnabled; }
    public int getFreezeIntervalTicks() { return freezeIntervalTicks; }
    public double getFreezeDamage() { return freezeDamage; }
    public int getFreezeSlowAmplifier() { return freezeSlowAmplifier; }
    public int getFreezeSlowDuration() { return freezeSlowDuration; }

    public boolean isLoreEnabled() { return loreEnabled; }

    public boolean isLeafParticlesEnabled() { return leafParticlesEnabled; }
}
