package io.Sriptirc_wp_1258.seasonalplugin.weather;

import io.Sriptirc_wp_1258.seasonalplugin.SeasonalPlugin;
import io.Sriptirc_wp_1258.seasonalplugin.config.ConfigManager;
import io.Sriptirc_wp_1258.seasonalplugin.season.Season;
import io.Sriptirc_wp_1258.seasonalplugin.season.SeasonManager;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 天气管理器 - 根据当前季节调整天气概率
 * 每 5 分钟检查并调整一次
 * 冬季：把已加载区块的 biome 温度改低，让原版自动下雨变下雪
 * 换季时自动恢复原 biome
 */
public class WeatherManager {

    private final SeasonalPlugin plugin;
    private final ConfigManager configManager;
    private final SeasonManager seasonManager;
    private final Random random = new Random();

    // 记录被改过的区块位置 -> 原 biome，用于换季恢复
    private final Map<String, Biome> modifiedBiomes = new HashMap<>();
    private Season lastSeason = null;

    // 温度低的雪地 biome（用于替换）
    private static final List<Biome> SNOW_BIOMES = List.of(
            Biome.SNOWY_PLAINS,
            Biome.SNOWY_TAIGA,
            Biome.SNOWY_SLOPES,
            Biome.GROVE,
            Biome.ICE_SPIKES
    );

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
     */
    public void applyWeather() {
        Season season = seasonManager.getCurrentSeason();

        // 检测换季：如果季节变了，恢复所有被改过的 biome
        if (lastSeason != null && lastSeason != season) {
            restoreAllBiomes();
        }
        lastSeason = season;

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

                // 冬季：把已加载区块的 biome 改成雪地类，让雨变雪
                if (season == Season.WINTER) {
                    applyWinterBiomes(world);
                }
            } else {
                world.setStorm(false);
                world.setThundering(false);
                world.setWeatherDuration(0);
            }
        }
    }

    /**
     * 冬季：把已加载区块的 biome 改成雪地 biome
     * 每个区块只改 4 个采样点（biome 是区域性的，不需要每个方块都改）
     */
    private void applyWinterBiomes(World world) {
        for (Chunk chunk : world.getLoadedChunks()) {
            int chunkX = chunk.getX() << 4;
            int chunkZ = chunk.getZ() << 4;

            // 每个区块只改 4 个角 + 中心，共 5 个点就够了
            // biome 在 4x4 区域内是连续的
            int[][] samplePoints = {
                    {4, 4}, {4, 12}, {12, 4}, {12, 12}, {8, 8}
            };

            for (int[] point : samplePoints) {
                int blockX = chunkX + point[0];
                int blockZ = chunkZ + point[1];

                Biome original = world.getBiome(blockX, 0, blockZ);
                if (isSnowBiome(original)) continue;

                String key = world.getName() + ":" + (chunk.getX()) + "," + (chunk.getZ());
                // 按区块记录，不按方块记录
                modifiedBiomes.putIfAbsent(key, original);

                Biome snowBiome = SNOW_BIOMES.get(random.nextInt(SNOW_BIOMES.size()));
                world.setBiome(blockX, 0, blockZ, snowBiome);
            }
        }
    }

    /**
     * 换季时恢复所有被改过的 biome
     */
    private void restoreAllBiomes() {
        if (modifiedBiomes.isEmpty()) return;

        plugin.getLogger().info("§b季节更替，恢复 " + modifiedBiomes.size() + " 个区块的原始 biome...");

        // 按世界分组恢复
        Map<String, Map<String, Biome>> byWorld = new HashMap<>();
        for (Map.Entry<String, Biome> entry : modifiedBiomes.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            byWorld.computeIfAbsent(parts[0], k -> new HashMap<>()).put(parts[1], entry.getValue());
        }

        for (Map.Entry<String, Map<String, Biome>> worldEntry : byWorld.entrySet()) {
            World world = plugin.getServer().getWorld(worldEntry.getKey());
            if (world == null) continue;

            for (Map.Entry<String, Biome> biomeEntry : worldEntry.getValue().entrySet()) {
                String[] coords = biomeEntry.getKey().split(",");
                int chunkX = Integer.parseInt(coords[0]);
                int chunkZ = Integer.parseInt(coords[1]);
                // 恢复区块中心点的 biome（4 个采样点都恢复）
                int[][] samplePoints = {{4, 4}, {4, 12}, {12, 4}, {12, 12}, {8, 8}};
                for (int[] point : samplePoints) {
                    int bx = (chunkX << 4) + point[0];
                    int bz = (chunkZ << 4) + point[1];
                    world.setBiome(bx, 0, bz, biomeEntry.getValue());
                }
            }
        }

        modifiedBiomes.clear();
        plugin.getLogger().info("§a原始 biome 已全部恢复");
    }

    private boolean isSnowBiome(Biome biome) {
        return SNOW_BIOMES.contains(biome);
    }

    /**
     * 立即刷新天气（用于换季时调用）
     */
    public void refreshWeather() {
        applyWeather();
    }
}
