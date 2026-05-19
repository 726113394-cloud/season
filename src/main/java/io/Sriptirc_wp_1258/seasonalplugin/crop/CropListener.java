package io.Sriptirc_wp_1258.seasonalplugin.crop;

import io.Sriptirc_wp_1258.seasonalplugin.SeasonalPlugin;
import io.Sriptirc_wp_1258.seasonalplugin.config.ConfigManager;
import io.Sriptirc_wp_1258.seasonalplugin.season.Season;
import io.Sriptirc_wp_1258.seasonalplugin.season.SeasonManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * 作物生长监听器
 * 根据季节调整生长速度 + 限制不同作物在不同季节生长 + 注入 Lore
 */
public class CropListener implements Listener {

    private final SeasonalPlugin plugin;
    private final ConfigManager configManager;
    private final SeasonManager seasonManager;
    private final CropLoreApplier loreApplier;
    private final Random random = new Random();

    public CropListener(SeasonalPlugin plugin, ConfigManager configManager,
                        SeasonManager seasonManager, CropLoreApplier loreApplier) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.seasonManager = seasonManager;
        this.loreApplier = loreApplier;
    }

    /**
     * 种植事件 - 限制不符合当前季节的作物被种下
     * 能种但长不大（阻止种植事件，提示玩家）
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!configManager.isCropGrowthEnabled()) return;

        Block block = event.getBlockPlaced();
        Material type = block.getType();

        // 只处理受季节限制的作物
        if (!CropRegistry.isSeasonRestricted(type)) return;

        Season season = seasonManager.getCurrentSeason();
        if (!CropRegistry.canGrowInSeason(type, season)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                    "§c❌ " + CropRegistry.formatCropName(type) + " 不适合在" + season.getColoredName() + "§c种植！"
            );
        }
    }

    /**
     * 作物生长事件 - 根据季节调整生长概率 + 季节限制
     * 不符合当前季节的作物直接阻止生长
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCropGrow(BlockGrowEvent event) {
        if (!configManager.isCropGrowthEnabled()) return;

        Block block = event.getBlock();
        if (!CropRegistry.isCrop(block)) return;

        Material cropType = block.getType();
        Season season = seasonManager.getCurrentSeason();

        // 季节限制：不允许生长的季节直接卡住
        if (CropRegistry.isSeasonRestricted(cropType) && !CropRegistry.canGrowInSeason(cropType, season)) {
            event.setCancelled(true);
            return;
        }

        double multiplier = configManager.getGrowthMultiplierForSeason(season.name().toLowerCase());

        // multiplier >= 1.0 时正常生长，< 1.0 时概率跳过生长
        if (multiplier < 1.0) {
            if (random.nextDouble() > multiplier) {
                event.setCancelled(true);
            }
        } else if (multiplier > 1.0) {
            // 生长加速：额外触发一次生长 tick
            if (random.nextDouble() < (multiplier - 1.0)) {
                BlockData data = block.getBlockData();
                if (data instanceof Ageable ageable) {
                    if (ageable.getAge() < ageable.getMaximumAge()) {
                        ageable.setAge(ageable.getAge() + 1);
                        block.setBlockData(ageable);
                    }
                }
            }
        }
        // multiplier == 1.0 不干预
    }

    /**
     * 玩家手持物品切换时 - 更新 Lore 显示
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        if (!configManager.isLoreEnabled()) return;

        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (item != null) {
            loreApplier.applyLore(item);
        }
    }

    /**
     * 玩家右键/左键方块时 - 更新手中物品 Lore
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!configManager.isLoreEnabled()) return;

        ItemStack item = event.getItem();
        if (item != null) {
            loreApplier.applyLore(item);
        }
    }
}
