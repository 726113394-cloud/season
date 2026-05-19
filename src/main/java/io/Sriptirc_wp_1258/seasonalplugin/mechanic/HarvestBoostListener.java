package io.Sriptirc_wp_1258.seasonalplugin.mechanic;

import io.Sriptirc_wp_1258.seasonalplugin.SeasonalPlugin;
import io.Sriptirc_wp_1258.seasonalplugin.config.ConfigManager;
import io.Sriptirc_wp_1258.seasonalplugin.crop.CropRegistry;
import io.Sriptirc_wp_1258.seasonalplugin.season.Season;
import io.Sriptirc_wp_1258.seasonalplugin.season.SeasonManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Random;

/**
 * 秋季收获翻倍机制
 * 秋季破坏成熟作物时，掉落物按配置倍率翻倍
 */
public class HarvestBoostListener implements Listener {

    private final ConfigManager configManager;
    private final SeasonManager seasonManager;
    private final Random random = new Random();

    public HarvestBoostListener(ConfigManager configManager, SeasonManager seasonManager) {
        this.configManager = configManager;
        this.seasonManager = seasonManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCropHarvest(BlockBreakEvent event) {
        if (!configManager.isHarvestBoostEnabled()) return;
        if (seasonManager.getCurrentSeason() != Season.AUTUMN) return;

        Block block = event.getBlock();
        if (!CropRegistry.isCrop(block) || !CropRegistry.isMature(block)) return;

        double multiplier = configManager.getHarvestBoostMultiplier();
        if (multiplier <= 1.0) return;

        // 获取原本的掉落物
        Collection<ItemStack> drops = block.getDrops(event.getPlayer().getInventory().getItemInMainHand());
        Location loc = block.getLocation();

        // 额外掉落 (multiplier - 1) 份
        int extraRolls = (int) Math.floor(multiplier - 1.0);
        double extraChance = (multiplier - 1.0) - extraRolls;

        for (ItemStack drop : drops) {
            // 整数倍直接加
            if (extraRolls > 0) {
                ItemStack extra = drop.clone();
                extra.setAmount(drop.getAmount() * extraRolls);
                loc.getWorld().dropItemNaturally(loc, extra);
            }
            // 小数概率额外掉落
            if (extraChance > 0 && random.nextDouble() < extraChance) {
                loc.getWorld().dropItemNaturally(loc, drop.clone());
            }
        }
    }
}
