package io.Sriptirc_wp_1258.seasonalplugin.mechanic;

import io.Sriptirc_wp_1258.seasonalplugin.SeasonalPlugin;
import io.Sriptirc_wp_1258.seasonalplugin.config.ConfigManager;
import io.Sriptirc_wp_1258.seasonalplugin.season.Season;
import io.Sriptirc_wp_1258.seasonalplugin.season.SeasonManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 冬季冻伤机制
 * 玩家同时没穿靴子 + 没穿护腿时，在户外会冻伤
 * 供暖区域内不受冻伤
 * 冻伤效果：缓慢 + 持续掉血（每分钟一次）
 */
public class FreezeListener implements Listener {

    private final SeasonalPlugin plugin;
    private final ConfigManager configManager;
    private final SeasonManager seasonManager;
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Set<UUID> inHeatArea = new HashSet<>(); // 记录在供暖区域内的玩家
    private HeatAreaManager heatAreaManager;

    public FreezeListener(SeasonalPlugin plugin, ConfigManager configManager, SeasonManager seasonManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.seasonManager = seasonManager;
    }

    public void setHeatAreaManager(HeatAreaManager heatAreaManager) {
        this.heatAreaManager = heatAreaManager;
    }

    /**
     * 启动冻伤检查定时器
     * 固定每分钟检查一次（1200 tick），伤害固定 1 点
     */
    public void startFreezeTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!configManager.isFreezeEnabled()) return;
                if (seasonManager.getCurrentSeason() != Season.WINTER) {
                    frozenPlayers.clear();
                    return;
                }

                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    checkAndApplyFreeze(player);
                }
            }
        }.runTaskTimer(plugin, 100L, 1200L); // 每分钟一次
    }

    private void checkAndApplyFreeze(Player player) {
        UUID uuid = player.getUniqueId();

        // 供暖区域内不受冻伤
        if (heatAreaManager != null && heatAreaManager.isInHeatArea(player.getLocation())) {
            frozenPlayers.remove(uuid);
            if (!inHeatArea.contains(uuid)) {
                inHeatArea.add(uuid);
                player.sendMessage("§6🔥 §e你进入了供暖区域，寒意消散了！");
            }
            return;
        } else {
            if (inHeatArea.contains(uuid)) {
                inHeatArea.remove(uuid);
                player.sendMessage("§b❄ §3你离开了供暖区域，寒冷再次袭来……");
            }
        }

        // 只对户外玩家生效（头顶有天空光照）
        if (!player.getWorld().hasSkyLight()) return;
        if (player.getLocation().getBlock().getLightFromSky() < 15) return;

        // 检查靴子和护腿
        boolean hasBoots = player.getInventory().getBoots() != null
                && player.getInventory().getBoots().getType() != Material.AIR;
        boolean hasLeggings = player.getInventory().getLeggings() != null
                && player.getInventory().getLeggings().getType() != Material.AIR;

        // 必须同时没穿靴子 AND 没穿护腿才触发
        if (hasBoots && hasLeggings) {
            frozenPlayers.remove(uuid);
            return;
        }

        if (!frozenPlayers.contains(uuid)) {
            frozenPlayers.add(uuid);
            player.sendMessage("§b❄ §3你感到刺骨的寒意……冻伤了！");
            player.sendMessage("§7💡 请穿上 §f靴子 §7和 §f护腿 §7来抵御寒冷！");
        }

        // 缓慢效果
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS,
                configManager.getFreezeSlowDuration(),
                configManager.getFreezeSlowAmplifier(),
                false, true
        ));

        // 伤害固定 1 点
        player.damage(1.0);
    }

    /**
     * 防止冻伤伤害被误判为其他伤害类型
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFreezeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!frozenPlayers.contains(event.getEntity().getUniqueId())) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.CUSTOM) {
            event.setDamage(1.0);
        }
    }
}
