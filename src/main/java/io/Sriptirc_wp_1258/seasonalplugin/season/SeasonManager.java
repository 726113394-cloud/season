package io.Sriptirc_wp_1258.seasonalplugin.season;

import io.Sriptirc_wp_1258.seasonalplugin.SeasonalPlugin;
import io.Sriptirc_wp_1258.seasonalplugin.config.ConfigManager;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;

/**
 * 季节管理器 - 负责季节切换、持久化、时间计算
 */
public class SeasonManager {

    private final SeasonalPlugin plugin;
    private final ConfigManager configManager;

    private Season currentSeason;
    private Instant seasonStartTime;
    private long seasonStartGameTime; // 游戏刻

    public SeasonManager(SeasonalPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currentSeason = Season.SPRING; // 默认春
        // 先给个默认值，避免为 0
        if (!plugin.getServer().getWorlds().isEmpty()) {
            this.seasonStartGameTime = plugin.getServer().getWorlds().get(0).getFullTime();
        }
        loadSeasonData();
    }

    /**
     * 获取当前季节
     */
    public Season getCurrentSeason() {
        return currentSeason;
    }

    /**
     * 手动设置季节
     */
    public void setSeason(Season season) {
        this.currentSeason = season;
        this.seasonStartTime = Instant.now();
        this.seasonStartGameTime = plugin.getServer().getWorlds().get(0).getFullTime();
        saveSeasonData();
        plugin.getServer().broadcastMessage("§6[四季] §e季节已变更为 " + season.getColoredName());
    }

    /**
     * 检查是否需要切换季节，并执行切换
     */
    public void tickSeason() {
        if (configManager.isUseRealTime()) {
            tickRealTime();
        } else {
            tickGameTime();
        }
    }

    private void tickRealTime() {
        if (seasonStartTime == null) {
            seasonStartTime = Instant.now();
            saveSeasonData();
            return;
        }

        long daysPassed = Duration.between(seasonStartTime, Instant.now()).toDays();
        int daysPerSeason = configManager.getRealTimeDaysPerSeason();

        if (daysPassed >= daysPerSeason) {
            int seasonsToAdvance = (int) (daysPassed / daysPerSeason);
            for (int i = 0; i < seasonsToAdvance; i++) {
                currentSeason = currentSeason.next();
            }
            seasonStartTime = Instant.now();
            saveSeasonData();
            plugin.getServer().broadcastMessage("§6[四季] §e季节更替！当前季节：" + currentSeason.getColoredName());
        }
    }

    private void tickGameTime() {
        long currentTime = plugin.getServer().getWorlds().get(0).getFullTime();
        long daysPerSeason = configManager.getMcDaysPerSeason();
        long ticksPerSeason = daysPerSeason * 24000L; // 1 MC 天 = 24000 tick

        if (seasonStartGameTime == 0) {
            seasonStartGameTime = currentTime;
            saveSeasonData();
            return;
        }

        long elapsed = currentTime - seasonStartGameTime;
        if (elapsed >= ticksPerSeason) {
            long seasonsToAdvance = elapsed / ticksPerSeason;
            for (int i = 0; i < seasonsToAdvance; i++) {
                currentSeason = currentSeason.next();
            }
            seasonStartGameTime = currentTime;
            saveSeasonData();
            plugin.getServer().broadcastMessage("§6[四季] §e季节更替！当前季节：" + currentSeason.getColoredName());
        }
    }

    /**
     * 获取距离下次换季的剩余时间描述
     */
    public String getTimeRemaining() {
        if (configManager.isUseRealTime()) {
            if (seasonStartTime == null) return "未知";
            long daysPassed = Duration.between(seasonStartTime, Instant.now()).toDays();
            int daysPerSeason = configManager.getRealTimeDaysPerSeason();
            long remaining = daysPerSeason - daysPassed;
            if (remaining <= 0) return "即将更替";
            return remaining + " 天";
        } else {
            long currentTime = plugin.getServer().getWorlds().get(0).getFullTime();
            long ticksPerSeason = (long) configManager.getMcDaysPerSeason() * 24000L;
            long elapsed = currentTime - seasonStartGameTime;
            long remainingTicks = ticksPerSeason - elapsed;
            if (remainingTicks <= 0) return "即将更替";
            long remainingDays = remainingTicks / 24000;
            return remainingDays + " 个MC天";
        }
    }

    /**
     * 获取当前季节的第几天（从 1 开始）
     */
    public int getDayOfSeason() {
        if (configManager.isUseRealTime()) {
            if (seasonStartTime == null) return 1;
            long daysPassed = Duration.between(seasonStartTime, Instant.now()).toDays();
            return (int) Math.min(daysPassed + 1, configManager.getRealTimeDaysPerSeason());
        } else {
            long currentTime = plugin.getServer().getWorlds().get(0).getFullTime();
            long ticksPerSeason = (long) configManager.getMcDaysPerSeason() * 24000L;
            long elapsed = currentTime - seasonStartGameTime;
            int day = (int) (elapsed / 24000) + 1;
            return Math.min(day, configManager.getMcDaysPerSeason());
        }
    }

    /**
     * 获取当前季节的总天数
     */
    public int getTotalDaysInSeason() {
        if (configManager.isUseRealTime()) {
            return configManager.getRealTimeDaysPerSeason();
        } else {
            return configManager.getMcDaysPerSeason();
        }
    }

    // ====== 持久化 ======

    private File getDataFile() {
        return new File(plugin.getDataFolder(), "seasondata.yml");
    }

    private void saveSeasonData() {
        File file = getDataFile();
        YamlConfiguration data = new YamlConfiguration();
        data.set("current-season", currentSeason.name());
        if (seasonStartTime != null) {
            data.set("season-start-time", seasonStartTime.toString());
        }
        data.set("season-start-game-time", seasonStartGameTime);
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "无法保存季节数据", e);
        }
    }

    private void loadSeasonData() {
        File file = getDataFile();
        if (!file.exists()) {
            // 首次启动，保存初始状态
            saveSeasonData();
            return;
        }

        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        String seasonName = data.getString("current-season");
        if (seasonName != null) {
            try {
                currentSeason = Season.valueOf(seasonName.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        String timeStr = data.getString("season-start-time");
        if (timeStr != null) {
            try {
                seasonStartTime = Instant.parse(timeStr);
            } catch (Exception ignored) {}
        }

        long savedTime = data.getLong("season-start-game-time", -1);
        if (savedTime >= 0) {
            seasonStartGameTime = savedTime;
        }
        // 如果 savedTime < 0（旧数据/不存在），保持构造里设的当前世界时间
    }
}
