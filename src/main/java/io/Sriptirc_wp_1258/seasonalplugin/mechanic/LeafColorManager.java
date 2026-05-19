package io.Sriptirc_wp_1258.seasonalplugin.mechanic;

import io.Sriptirc_wp_1258.seasonalplugin.SeasonalPlugin;
import io.Sriptirc_wp_1258.seasonalplugin.config.ConfigManager;
import io.Sriptirc_wp_1258.seasonalplugin.season.Season;
import io.Sriptirc_wp_1258.seasonalplugin.season.SeasonManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * 树叶季节颜色管理器
 * 根据季节在树叶周围生成不同颜色的粒子效果
 * 春 - 嫩绿  夏 - 深绿  秋 - 橙黄  冬 - 枯枝（无粒子/白色）
 */
public class LeafColorManager {

    private final SeasonalPlugin plugin;
    private final SeasonManager seasonManager;
    private final ConfigManager configManager;
    private final Random random = new Random();

    // 受影响的树叶类型
    private static final Set<Material> LEAF_TYPES = new HashSet<>();

    static {
        LEAF_TYPES.add(Material.OAK_LEAVES);
        LEAF_TYPES.add(Material.BIRCH_LEAVES);
        LEAF_TYPES.add(Material.SPRUCE_LEAVES);
        LEAF_TYPES.add(Material.JUNGLE_LEAVES);
        LEAF_TYPES.add(Material.ACACIA_LEAVES);
        LEAF_TYPES.add(Material.DARK_OAK_LEAVES);
        LEAF_TYPES.add(Material.CHERRY_LEAVES);
        LEAF_TYPES.add(Material.AZALEA_LEAVES);
        LEAF_TYPES.add(Material.FLOWERING_AZALEA_LEAVES);
        LEAF_TYPES.add(Material.MANGROVE_LEAVES);
    }

    // 每个季节对应的粒子颜色
    private static final java.util.Map<Season, Particle.DustOptions> SEASON_PARTICLE_COLORS = java.util.Map.of(
            Season.SPRING, new Particle.DustOptions(Color.fromRGB(100, 200, 80), 1.0f),    // 嫩绿
            Season.SUMMER, new Particle.DustOptions(Color.fromRGB(30, 150, 30), 1.0f),      // 深绿
            Season.AUTUMN, new Particle.DustOptions(Color.fromRGB(220, 140, 30), 1.0f),     // 橙黄
            Season.WINTER, new Particle.DustOptions(Color.fromRGB(220, 220, 230), 1.0f)     // 灰白
    );

    public LeafColorManager(SeasonalPlugin plugin, SeasonManager seasonManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
        this.configManager = configManager;
    }

    /**
     * 启动树叶粒子效果定时器
     * 每 3 秒在加载的树叶周围生成粒子
     */
    public void startLeafParticleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!configManager.isLeafParticlesEnabled()) return;

                Season season = seasonManager.getCurrentSeason();
                // 冬季粒子少一点（枯枝感）
                int rate = (season == Season.WINTER) ? 4 : 2;

                // 遍历所有在线玩家附近的树叶
                plugin.getServer().getOnlinePlayers().forEach(player -> {
                    Location loc = player.getLocation();
                    int radius = 16; // 只处理玩家周围 16 格

                    for (int x = -radius; x <= radius; x += rate) {
                        for (int y = -radius; y <= radius; y += rate) {
                            for (int z = -radius; z <= radius; z += rate) {
                                Block block = loc.clone().add(x, y, z).getBlock();
                                if (isLeafBlock(block)) {
                                    spawnLeafParticle(block, season);
                                }
                            }
                        }
                    }
                });
            }
        }.runTaskTimer(plugin, 60L, 60L); // 每 3 秒
    }

    /**
     * 判断是否是树叶方块
     */
    private boolean isLeafBlock(Block block) {
        return LEAF_TYPES.contains(block.getType());
    }

    /**
     * 在树叶位置生成季节对应的粒子
     */
    private void spawnLeafParticle(Block block, Season season) {
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);

        // 随机偏移，让粒子从树叶周围飘出
        double offsetX = (random.nextDouble() - 0.5) * 1.2;
        double offsetY = (random.nextDouble() - 0.5) * 1.2;
        double offsetZ = (random.nextDouble() - 0.5) * 1.2;

        // 每个树叶 tick 有概率生成粒子（避免太多）
        if (random.nextInt(3) != 0) return;

        Particle.DustOptions dustOptions = SEASON_PARTICLE_COLORS.get(season);

        // 秋季用 FALLING_LEAVES 粒子（如果有），否则用红石粒子模拟颜色
        try {
            if (season == Season.AUTUMN) {
                // 尝试使用 FALLING_LEAVES 粒子（1.21 可能有）
                try {
                    Particle fallingLeaves = Particle.valueOf("FALLING_LEAVES");
                    block.getWorld().spawnParticle(fallingLeaves, loc, 1, offsetX, offsetY, offsetZ, 0);
                } catch (IllegalArgumentException e) {
                    // 回退到红石粒子
                    block.getWorld().spawnParticle(Particle.DUST, loc, 1, offsetX, offsetY, offsetZ, 0, dustOptions);
                }
            } else if (season == Season.WINTER) {
                // 冬季用雪花粒子
                try {
                    Particle snowflake = Particle.valueOf("SNOWFLAKE");
                    block.getWorld().spawnParticle(snowflake, loc, 1, offsetX, offsetY, offsetZ, 0);
                } catch (IllegalArgumentException e) {
                    block.getWorld().spawnParticle(Particle.DUST, loc, 1, offsetX, offsetY, offsetZ, 0, dustOptions);
                }
            } else {
                // 春夏用彩色粒子
                block.getWorld().spawnParticle(Particle.DUST, loc, 1, offsetX, offsetY, offsetZ, 0, dustOptions);
            }
        } catch (Exception ignored) {
            // 兜底：啥都不做
        }
    }
}
