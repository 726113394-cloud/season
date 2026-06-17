package io.Sriptirc_wp_1258.seasonalplugin.season;

import io.Sriptirc_wp_1258.seasonalplugin.SeasonalPlugin;
import io.Sriptirc_wp_1258.seasonalplugin.crop.CropRegistry;
import io.Sriptirc_wp_1258.seasonalplugin.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * 季节提示管理器
 * - 玩家上线时提示当前季节、第几天、适合种什么
 * - 换季时全服广播
 * - 每天首次天亮时提示
 */
public class SeasonTipManager implements Listener {

    private final SeasonalPlugin plugin;
    private final SeasonManager seasonManager;
    private final ConfigManager configManager;

    // 记录上次提示的游戏天，避免一天提示多次
    private long lastTipDay = -1;

    public SeasonTipManager(SeasonalPlugin plugin, SeasonManager seasonManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
        this.configManager = configManager;
    }

    /**
     * 玩家上线时发送季节提示
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 延迟一 tick 等玩家加载完
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sendSeasonTip(player);
        }, 20L);
    }

    /**
     * 启动每日天亮提示定时器
     * 每 20 秒检查一次是否天亮且新的一天
     */
    public void startDailyTipTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // 只在主世界检查
                var worlds = plugin.getServer().getWorlds();
                if (worlds.isEmpty()) return;

                var world = worlds.get(0);
                long time = world.getFullTime();
                long day = time / 24000;

                // 检查是否是新的一天（且不是刚启动）
                if (day != lastTipDay && lastTipDay != -1) {
                    lastTipDay = day;
                    // 0刻太阳升起时提示全服
                    long timeOfDay = time % 24000;
                    if (timeOfDay >= 0 && timeOfDay < 2000) {
                        Bukkit.broadcastMessage(buildDailyBroadcast());
                    }
                } else if (lastTipDay == -1) {
                    lastTipDay = day;
                }
            }
        }.runTaskTimer(plugin, 200L, 400L); // 每 20 秒检查一次
    }

    /**
     * 向单个玩家发送季节提示
     */
    public void sendSeasonTip(Player player) {
        Season season = seasonManager.getCurrentSeason();
        int day = seasonManager.getDayOfSeason();
        int total = seasonManager.getTotalDaysInSeason();

        player.sendMessage("");
        player.sendMessage("§6✦ §e季节报告 §6✦");
        player.sendMessage("§e当前季节: " + season.getColoredName() +
                " §7(第 §f" + day + "§7/§f" + total + " §7天)");
        player.sendMessage("§e距下次换季: §f" + seasonManager.getTimeRemaining());

        // 推荐作物
        List<String> recommended = CropRegistry.getRecommendedCrops(season);
        if (!recommended.isEmpty()) {
            player.sendMessage("§e适合种植: §a" + String.join("§7, §a", recommended));
        }

        // 季节特殊提示
        String seasonTip = getSeasonTip(season);
        if (seasonTip != null) {
            player.sendMessage("§e小贴士: §f" + seasonTip);
        }

        player.sendMessage("");
    }

    /**
     * 构建每日全服广播
     */
    public String buildDailyBroadcast() {
        Season season = seasonManager.getCurrentSeason();
        int day = seasonManager.getDayOfSeason();
        int total = seasonManager.getTotalDaysInSeason();

        StringBuilder sb = new StringBuilder();
        sb.append("\n§6✦ §e今日季节报告 §6✦\n");
        sb.append("§e").append(season.getColoredName()).append(" §7第 §f").append(day)
                .append("§7/§f").append(total).append(" §7天\n");

        List<String> recommended = CropRegistry.getRecommendedCrops(season);
        if (!recommended.isEmpty()) {
            sb.append("§e今日宜种: §a").append(String.join("§7, §a", recommended)).append("\n");
        }

        String tip = getSeasonTip(season);
        if (tip != null) {
            sb.append("§e提示: §f").append(tip);
        }

        return sb.toString();
    }

    /**
     * 获取季节小贴士
     */
    private String getSeasonTip(Season season) {
        return switch (season) {
            case SPRING -> "万物复苏，作物生长速度最快！钓鱼也有加成哦~";
            case SUMMER -> "天气炎热，适合种可可豆和下界疣，钓鱼依然有加成！";
            case AUTUMN -> "收获的季节！收割作物有额外掉落！抓紧囤粮过冬~";
            case WINTER -> "天寒地冻，记得穿好靴子和护腿！大部分作物无法生长。";
        };
    }
}
