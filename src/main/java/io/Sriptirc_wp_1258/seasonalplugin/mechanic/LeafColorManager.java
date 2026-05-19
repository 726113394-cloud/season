package io.Sriptirc_wp_1258.seasonalplugin.mechanic;

import io.Sriptirc_wp_1258.seasonalplugin.SeasonalPlugin;
import io.Sriptirc_wp_1258.seasonalplugin.config.ConfigManager;
import io.Sriptirc_wp_1258.seasonalplugin.season.Season;
import io.Sriptirc_wp_1258.seasonalplugin.season.SeasonManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 树叶季节颜色管理器
 *
 * 1. biome 切换：春→FOREST 夏→原样 秋→SAVANNA 冬→SNOWY_TAIGA
 * 2. 春季中期（第34%~66%）：橡树树叶 → 樱花树叶（视觉欺骗）
 *    过了中期自动恢复，破坏时掉橡树树叶
 *
 * 注意：白桦和云杉树叶颜色是硬编码的，不受 biome 影响
 */
public class LeafColorManager implements Listener {

    private final SeasonalPlugin plugin;
    private final SeasonManager seasonManager;
    private final ConfigManager configManager;

    // biome 记录
    private final Map<String, Biome> modifiedBiomes = new HashMap<>();
    private Season lastSeason = null;

    // 樱花替换记录：key = "世界名:x,y,z" -> 原 BlockData（用于恢复）
    private final Map<String, BlockData> cherryReplaced = new HashMap<>();
    private boolean wasInCherryPeriod = false; // 上一轮是否在开花期
    private boolean forcedBlossom = false;     // 是否强制开花（锁定到夏季）

    // 春季三段比例
    private static final double SPRING_EARLY_END = 0.33;   // 初春结束
    private static final double SPRING_MID_END = 0.66;     // 仲春（开花）结束

    // 各季节对应的目标 biome
    private static final Map<Season, List<Biome>> SEASON_BIOMES = Map.of(
            Season.SPRING, List.of(Biome.FOREST, Biome.BIRCH_FOREST, Biome.DARK_FOREST, Biome.FLOWER_FOREST),
            Season.SUMMER, List.of(),
            Season.AUTUMN, List.of(Biome.SAVANNA, Biome.SAVANNA_PLATEAU, Biome.WINDSWEPT_SAVANNA, Biome.TAIGA),
            Season.WINTER, List.of(Biome.SNOWY_TAIGA, Biome.SNOWY_PLAINS, Biome.GROVE, Biome.SNOWY_SLOPES)
    );

    private static final Set<Biome> SKIP_BIOMES = Set.of(
            Biome.OCEAN, Biome.DEEP_OCEAN, Biome.WARM_OCEAN, Biome.LUKEWARM_OCEAN,
            Biome.COLD_OCEAN, Biome.DEEP_COLD_OCEAN, Biome.DEEP_LUKEWARM_OCEAN,
            Biome.DEEP_FROZEN_OCEAN, Biome.FROZEN_OCEAN,
            Biome.RIVER, Biome.FROZEN_RIVER,
            Biome.DESERT, Biome.BADLANDS, Biome.WOODED_BADLANDS, Biome.ERODED_BADLANDS,
            Biome.NETHER_WASTES, Biome.SOUL_SAND_VALLEY, Biome.CRIMSON_FOREST,
            Biome.WARPED_FOREST, Biome.BASALT_DELTAS,
            Biome.THE_END, Biome.END_HIGHLANDS, Biome.END_MIDLANDS, Biome.SMALL_END_ISLANDS, Biome.END_BARRENS
    );

    public LeafColorManager(SeasonalPlugin plugin, SeasonManager seasonManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
        this.configManager = configManager;
    }

    /**
     * 强制立即刷新树叶和 biome（供管理员命令调用）
     */
    public void forceRefresh() {
        Season season = seasonManager.getCurrentSeason();
        // 先恢复所有
        restoreAllBiomes();
        restoreAllCherryLeaves();
        wasInCherryPeriod = false;

        // 重新应用
        if (season != Season.SUMMER) {
            List<Biome> targetBiomes = SEASON_BIOMES.get(season);
            if (targetBiomes != null && !targetBiomes.isEmpty()) {
                applySeasonBiomes(targetBiomes);
            }
        }
        if (season == Season.SPRING) {
            handleCherryBlossom();
        }
    }

    /**
     * 强制进入花期（无视春季进度），锁定到夏季来临
     * 返回 true 表示成功，false 表示当前不是春季
     */
    public boolean forceBlossomStart() {
        if (seasonManager.getCurrentSeason() != Season.SPRING) {
            return false;
        }
        restoreAllCherryLeaves();
        cherryReplaced.clear();
        applyCherryReplacement();
        wasInCherryPeriod = true;
        forcedBlossom = true;
        return true;
    }

    /**
     * 强制结束花期
     */
    public void forceBlossomEnd() {
        restoreAllCherryLeaves();
        wasInCherryPeriod = false;
        forcedBlossom = false;
    }

    /**
     * 启动定时任务，每 30 秒检查一次
     */
    public void startBiomeTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!configManager.isLeafParticlesEnabled()) return;

                Season season = seasonManager.getCurrentSeason();

                // 检测换季
                if (lastSeason != null && lastSeason != season) {
                    restoreAllBiomes();
                    // 如果是春季→夏季，且是强制开花状态，自动结束
                    if (lastSeason == Season.SPRING && season == Season.SUMMER && forcedBlossom) {
                        forcedBlossom = false;
                    }
                    restoreAllCherryLeaves();
                    wasInCherryPeriod = false;
                }
                lastSeason = season;

                // biome 切换（夏季不切）
                if (season != Season.SUMMER) {
                    List<Biome> targetBiomes = SEASON_BIOMES.get(season);
                    if (targetBiomes != null && !targetBiomes.isEmpty()) {
                        applySeasonBiomes(targetBiomes);
                    }
                }

                // 樱花替换逻辑
                if (season == Season.SPRING) {
                    // 强制开花状态下，不按进度走，一直保持
                    if (!forcedBlossom) {
                        handleCherryBlossom();
                    }
                } else {
                    // 其他季节如果有残留的樱花，恢复
                    if (!cherryReplaced.isEmpty()) {
                        restoreAllCherryLeaves();
                    }
                    wasInCherryPeriod = false;
                    forcedBlossom = false;
                }
            }
        }.runTaskTimer(plugin, 100L, 600L); // 每 30 秒
    }

    // ==================== 樱花替换 ====================

    /**
     * 春季樱花逻辑：初春→无，仲春→替换，晚春→恢复
     */
    private void handleCherryBlossom() {
        double progress = getSpringProgress();
        boolean inCherryPeriod = (progress >= SPRING_EARLY_END && progress < SPRING_MID_END);

        if (inCherryPeriod) {
            // 进入开花期：替换橡树树叶为樱花树叶
            if (!wasInCherryPeriod) {
                plugin.getLogger().info("§d🌸 春季花开！橡树化作樱花...");
            }
            applyCherryReplacement();
            wasInCherryPeriod = true;
        } else if (wasInCherryPeriod) {
            // 离开开花期：恢复橡树树叶
            plugin.getLogger().info("§a花期结束，樱花散去...");
            restoreAllCherryLeaves();
            wasInCherryPeriod = false;
        }
        // 初春或晚春但之前不在开花期：啥也不做
    }

    /**
     * 获取春季进度（0.0 ~ 1.0）
     */
    private double getSpringProgress() {
        int day = seasonManager.getDayOfSeason();
        int total = seasonManager.getTotalDaysInSeason();
        return (double) (day - 1) / (double) total;
    }

    /**
     * 把已加载区块的橡树树叶替换为樱花树叶
     */
    private void applyCherryReplacement() {
        for (World world : plugin.getServer().getWorlds()) {
            if (!world.hasSkyLight()) continue;

            for (Chunk chunk : world.getLoadedChunks()) {
                int chunkX = chunk.getX() << 4;
                int chunkZ = chunk.getZ() << 4;

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int bx = chunkX + x;
                        int bz = chunkZ + z;

                        // 只检查地表到地表+16 高度的橡树树叶
                        int minY = world.getMinHeight();
                        int maxY = world.getMaxHeight();

                        for (int y = minY; y < maxY; y++) {
                            Block block = world.getBlockAt(bx, y, bz);
                            if (block.getType() == Material.OAK_LEAVES
                                    || block.getType() == Material.DARK_OAK_LEAVES) {

                                String key = world.getName() + ":" + bx + "," + y + "," + bz;
                                // 只记录一次
                                if (!cherryReplaced.containsKey(key)) {
                                    cherryReplaced.put(key, block.getBlockData());
                                }
                                block.setType(Material.CHERRY_LEAVES, false);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 恢复所有被替换的樱花树叶为橡树树叶
     */
    private void restoreAllCherryLeaves() {
        if (cherryReplaced.isEmpty()) return;

        for (Map.Entry<String, BlockData> entry : cherryReplaced.entrySet()) {
            String[] parts = entry.getKey().split(":");
            String worldName = parts[0];
            String[] coords = parts[1].split(",");

            World world = plugin.getServer().getWorld(worldName);
            if (world == null) continue;

            int x = Integer.parseInt(coords[0]);
            int y = Integer.parseInt(coords[1]);
            int z = Integer.parseInt(coords[2]);

            Block block = world.getBlockAt(x, y, z);
            // 只恢复仍然是樱花树叶的方块（玩家手动放的不管）
            if (block.getType() == Material.CHERRY_LEAVES) {
                block.setType(Material.OAK_LEAVES, false);
                block.setBlockData(entry.getValue(), false);
            }
        }

        cherryReplaced.clear();
    }

    /**
     * 破坏樱花树叶时掉落橡树树叶
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCherryLeafBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHERRY_LEAVES) return;

        // 检查这块是不是我们替换的
        String key = block.getWorld().getName() + ":" + block.getX() + ","
                + block.getY() + "," + block.getZ();

        if (cherryReplaced.containsKey(key)) {
            // 取消原版掉落
            event.setDropItems(false);
            // 掉落橡树树叶
            Location loc = block.getLocation();
            loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.OAK_LEAVES));
        }
    }

    // ==================== biome 切换 ====================

    private void applySeasonBiomes(List<Biome> targetBiomes) {
        Random random = new Random();

        for (World world : plugin.getServer().getWorlds()) {
            if (!world.hasSkyLight()) continue;

            for (Chunk chunk : world.getLoadedChunks()) {
                int chunkX = chunk.getX() << 4;
                int chunkZ = chunk.getZ() << 4;

                int[][] samplePoints = {{4, 4}, {4, 12}, {12, 4}, {12, 12}, {8, 8}};

                for (int[] point : samplePoints) {
                    int bx = chunkX + point[0];
                    int bz = chunkZ + point[1];

                    Biome original = world.getBiome(bx, 0, bz);
                    if (SKIP_BIOMES.contains(original)) continue;
                    if (targetBiomes.contains(original)) continue;

                    String key = world.getName() + ":" + chunk.getX() + "," + chunk.getZ();
                    modifiedBiomes.putIfAbsent(key, original);

                    Biome target = targetBiomes.get(random.nextInt(targetBiomes.size()));
                    world.setBiome(bx, 0, bz, target);
                }
            }
        }
    }

    private void restoreAllBiomes() {
        if (modifiedBiomes.isEmpty()) return;

        plugin.getLogger().info("§b季节更替，恢复 " + modifiedBiomes.size() + " 个区块的原始 biome...");

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
}
