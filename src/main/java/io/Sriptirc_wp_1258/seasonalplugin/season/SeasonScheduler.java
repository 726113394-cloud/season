package io.Sriptirc_wp_1258.seasonalplugin.season;

import io.Sriptirc_wp_1258.seasonalplugin.SeasonalPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 季节定时任务 - 定期检查是否需要换季
 * 每 5 分钟（现实时间模式）或每 100 tick（游戏时间模式）检查一次
 */
public class SeasonScheduler {

    private final SeasonalPlugin plugin;
    private final SeasonManager seasonManager;

    public SeasonScheduler(SeasonalPlugin plugin, SeasonManager seasonManager) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
    }

    public void start() {
        // 每 6000 tick = 5 分钟检查一次，够用又不费性能
        new BukkitRunnable() {
            @Override
            public void run() {
                seasonManager.tickSeason();
            }
        }.runTaskTimer(plugin, 200L, 6000L);
    }
}
