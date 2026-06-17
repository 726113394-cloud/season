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
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 树叶季节颜色管理器
 *
 * 1. biome 切换：根据季节切换已加载区块的 biome
 *    春→温带森林类  夏→原样  秋→热带/针叶类  冬→雪地类
 *    兼容 Terralith 等自定义 biome 数据包
 *
 * 2. 春季中期（第34%~66%）：橡树树叶 → 樱花树叶（视觉欺骗）
 *    新加载的区块也会自动替换，不会断层
 *    破坏时掉橡树树叶
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

    // 樱花替换记录
    private final Map<String, BlockData> cherryReplaced = new HashMap<>();
    private boolean wasInCherryPeriod = false;
    private boolean forcedBlossom = false;

    // 春季三段比例
    private static final double SPRING_EARLY_END = 0.33;
    private static final double SPRING_MID_END = 0.66;

    // ====== biome 匹配策略 ======
    // 只跳过极端 biome（海洋、沙漠、下界、末地等），其他全部切换
    // 兼容 Terralith 等任何自定义 biome 数据包

    // 需要跳过的 biome key 关键词
    private static final Set<String> SKIP_BIOME_KEYWORDS = Set.of(
            "ocean", "river", "desert", "badlands", "nether", "crimson",
            "warped", "soul", "basalt", "end_", "the_end", "small_end",
            "mushroom", "deep_ocean", "frozen_ocean"
    );

    // 已经是目标类型的 biome 关键词（不重复切换）
    private static final Set<String> SPRING_TARGET_KEYWORDS = Set.of(
            "forest", "birch", "flower", "plains", "meadow", "cherry",
            "jungle", "swamp", "mangrove"
    );

    private static final Set<String> AUTUMN_TARGET_KEYWORDS = Set.of(
            "savanna", "taiga", "windswept"
    );

    private static final Set<String> WINTER_TARGET_KEYWORDS = Set.of(
            "snowy", "ice", "frozen", "grove", "slopes"
    );

    public LeafColorManager(SeasonalPlugin plugin, SeasonManager seasonManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
        this.configManager = configManager;
    }

    /**
     * 强制立即刷新树叶和 biome
     */
    public void forceRefresh() {
        Season season = seasonManager.getCurrentSeason();
        restoreAllBiomes();
        restoreAllCherryLeaves();
        wasInCherryPeriod = false;

        if (season != Season.SUMMER) {
            applySeasonBiomes(season);
        }
        if (season == Season.SPRING) {
            handleCherryBlossom();
        }
    }

    /**
     * 强制进入花期
     */
    public boolean forceBlossomStart() {
        if (seasonManager.getCurrentSeason() != Season.SPRING) return false;
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

    // ==================== 定时任务 ====================

    public void startBiomeTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!configManager.isLeafParticlesEnabled()) return;

                Season season = seasonManager.getCurrentSeason();

                if (lastSeason != null && lastSeason != season) {
                    restoreAllBiomes();
                    if (lastSeason == Season.SPRING && season == Season.SUMMER && forcedBlossom) {
                        forcedBlossom = false;
                    }
                    restoreAllCherryLeaves();
                    wasInCherryPeriod = false;
                }
                lastSeason = season;

                if (season != Season.SUMMER) {
                    applySeasonBiomes(season);
                }

                if (season == Season.SPRING) {
                    if (!forcedBlossom) handleCherryBlossom();
                } else {
                    if (!cherryReplaced.isEmpty()) restoreAllCherryLeaves();
                    wasInCherryPeriod = false;
                    forcedBlossom = false;
                }
            }
        }.runTaskTimer(plugin, 100L, 600L);
    }

    /**
     * 新区块加载时自动应用 biome 和樱花替换
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!configManager.isLeafParticlesEnabled()) return;

        Season season = seasonManager.getCurrentSeason();
        Chunk chunk = event.getChunk();

        // biome 切换
        if (season != Season.SUMMER) {
            applyBiomesToChunk(chunk, season);
        }

        // 樱花替换
        if (season == Season.SPRING) {
            boolean inCherryPeriod = forcedBlossom ||
                    (getSpringProgress() >= SPRING_EARLY_END && getSpringProgress() < SPRING_MID_END);
            if (inCherryPeriod) {
                applyCherryToChunk(chunk);
            }
        }
    }

    // ==================== biome 切换（关键词匹配，兼容 Terralith） ====================

    /**
     * 对所有已加载区块应用季节 biome
     */
    private void applySeasonBiomes(Season season) {
        for (World world : plugin.getServer().getWorlds()) {
            if (!world.hasSkyLight()) continue;
            for (Chunk chunk : world.getLoadedChunks()) {
                applyBiomesToChunk(chunk, season);
            }
        }
    }

    /**
     * 对单个区块应用季节 biome
     */
    private void applyBiomesToChunk(Chunk chunk, Season season) {
        World world = chunk.getWorld();
        int chunkX = chunk.getX() << 4;
        int chunkZ = chunk.getZ() << 4;

        int[][] samplePoints = {{4, 4}, {4, 12}, {12, 4}, {12, 12}, {8, 8}};

        for (int[] point : samplePoints) {
            int bx = chunkX + point[0];
            int bz = chunkZ + point[1];

            Biome original = world.getBiome(bx, 0, bz);
            String biomeKey = original.getKey().toString().toLowerCase();

            // 跳过不该改的 biome
            if (shouldSkipBiome(biomeKey)) continue;

            String key = world.getName() + ":" + chunk.getX() + "," + chunk.getZ();
            modifiedBiomes.putIfAbsent(key, original);

            // 根据季节选目标 biome
            Biome target = getTargetBiome(original, season);
            if (target != null && target != original) {
                world.setBiome(bx, 0, bz, target);
            }
        }
    }

    /**
     * 判断 biome 是否应该被跳过
     */
    private boolean shouldSkipBiome(String biomeKey) {
        for (String keyword : SKIP_BIOME_KEYWORDS) {
            if (biomeKey.contains(keyword)) return true;
        }
        return false;
    }

    /**
     * 根据季节选择目标 biome
     * 只要不是目标类型就切换（兼容 Terralith）
     */
    private Biome getTargetBiome(Biome original, Season season) {
        String key = original.getKey().toString().toLowerCase();

        return switch (season) {
            case SPRING -> {
                if (containsKeyword(key, SPRING_TARGET_KEYWORDS)) yield null;
                yield Biome.FOREST;
            }
            case AUTUMN -> {
                if (containsKeyword(key, AUTUMN_TARGET_KEYWORDS)) yield null;
                yield Biome.SAVANNA;
            }
            case WINTER -> {
                if (containsKeyword(key, WINTER_TARGET_KEYWORDS)) yield null;
                yield Biome.SNOWY_TAIGA;
            }
            default -> null;
        };
    }

    private boolean containsKeyword(String key, Set<String> keywords) {
        for (String kw : keywords) {
            if (key.contains(kw)) return true;
        }
        return false;
    }

    // ==================== 樱花替换 ====================

    private void handleCherryBlossom() {
        double progress = getSpringProgress();
        boolean inCherryPeriod = progress >= SPRING_EARLY_END && progress < SPRING_MID_END;

        if (inCherryPeriod) {
            if (!wasInCherryPeriod) {
                plugin.getLogger().info("§d🌸 春季花开！橡树化作樱花...");
            }
            applyCherryReplacement();
            wasInCherryPeriod = true;
        } else if (wasInCherryPeriod) {
            plugin.getLogger().info("§a花期结束，樱花散去...");
            restoreAllCherryLeaves();
            wasInCherryPeriod = false;
        }
    }

    private double getSpringProgress() {
        int day = seasonManager.getDayOfSeason();
        int total = seasonManager.getTotalDaysInSeason();
        return (double) (day - 1) / (double) total;
    }

    /**
     * 对所有已加载区块应用樱花替换
     */
    private void applyCherryReplacement() {
        for (World world : plugin.getServer().getWorlds()) {
            if (!world.hasSkyLight()) continue;
            for (Chunk chunk : world.getLoadedChunks()) {
                applyCherryToChunk(chunk);
            }
        }
    }

    /**
     * 对单个区块应用樱花替换
     */
    private void applyCherryToChunk(Chunk chunk) {
        World world = chunk.getWorld();
        int chunkX = chunk.getX() << 4;
        int chunkZ = chunk.getZ() << 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int bx = chunkX + x;
                int bz = chunkZ + z;

                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    Block block = world.getBlockAt(bx, y, bz);
                    if (block.getType() == Material.OAK_LEAVES
                            || block.getType() == Material.DARK_OAK_LEAVES) {

                        String key = world.getName() + ":" + bx + "," + y + "," + bz;
                        if (!cherryReplaced.containsKey(key)) {
                            cherryReplaced.put(key, block.getBlockData());
                        }
                        block.setType(Material.CHERRY_LEAVES, false);
                    }
                }
            }
        }
    }

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
            if (block.getType() == Material.CHERRY_LEAVES) {
                block.setType(Material.OAK_LEAVES, false);
                block.setBlockData(entry.getValue(), false);
            }
        }
        cherryReplaced.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCherryLeafBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHERRY_LEAVES) return;

        String key = block.getWorld().getName() + ":" + block.getX() + ","
                + block.getY() + "," + block.getZ();

        if (cherryReplaced.containsKey(key)) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.OAK_LEAVES));
        }
    }

    // ==================== biome 恢复 ====================

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
