package io.Sriptirc_wp_1258.seasonalplugin.weather;

import io.Sriptirc_wp_1258.seasonalplugin.SeasonalPlugin;
import io.Sriptirc_wp_1258.seasonalplugin.config.ConfigManager;
import io.Sriptirc_wp_1258.seasonalplugin.season.Season;
import io.Sriptirc_wp_1258.seasonalplugin.season.SeasonManager;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

/**
 * 天气管理器 - 根据当前季节调整天气概率
 * 每 5 分钟检查并调整一次
 * biome 切换由 LeafColorManager 统一管理
 */
public class WeatherManager {

    private final SeasonalPlugin plugin;
    private final ConfigManager configManager;
    private final SeasonManager seasonManager;
    private final Random random = new Random();

    public WeatherManager(SeasonalPlugin plugin, ConfigManager configManager, SeasonManager seasonManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.seasonManager = seasonManager;
    }

    public void start() {
        if (!configManager.isWeatherEnabled()) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                applyWeather();
            }
        }.runTaskTimer(plugin, 100L, 6000L); // 每 5 分钟
    }

    /**
     * 根据当前季节调整所有世界的天气
     * biome 切换由 LeafColorManager 处理，这里只控制下雨/雷暴概率
     */
    public void applyWeather() {
        Season season = seasonManager.getCurrentSeason();
        String seasonName = season.name().toLowerCase();

        double rainChance = configManager.getRainChanceForSeason(seasonName);
        double stormChance = configManager.getStormChanceForSeason(seasonName);

        for (World world : plugin.getServer().getWorlds()) {
            if (!world.hasSkyLight()) continue;

            boolean shouldRain = random.nextDouble() < rainChance;
            boolean shouldStorm = shouldRain && random.nextDouble() < stormChance;

            if (shouldRain) {
                world.setStorm(true);
                world.setThundering(shouldStorm);
                world.setWeatherDuration(12000 + random.nextInt(6000));
                world.setThunderDuration(shouldStorm ? 6000 + random.nextInt(4000) : 0);
            } else {
                world.setStorm(false);
                world.setThundering(false);
                world.setWeatherDuration(0);
            }
        }
    }

    /**
     * 立即刷新天气（用于换季时调用）
     */
    public void refreshWeather() {
        applyWeather();
    }
}
