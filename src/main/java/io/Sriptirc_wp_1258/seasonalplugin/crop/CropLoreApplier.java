package io.Sriptirc_wp_1258.seasonalplugin.crop;

import io.Sriptirc_wp_1258.seasonalplugin.config.ConfigManager;
import io.Sriptirc_wp_1258.seasonalplugin.season.Season;
import io.Sriptirc_wp_1258.seasonalplugin.season.SeasonManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 作物 Lore 注入器
 * 在种子/作物物品上显示四季适配表
 */
public class CropLoreApplier {

    private final ConfigManager configManager;
    private final SeasonManager seasonManager;

    // 每个季节的生长评级
    private static final java.util.Map<Season, String> GROWTH_RATINGS = java.util.Map.of(
            Season.SPRING, "§a旺盛",
            Season.SUMMER, "§e良好",
            Season.AUTUMN, "§6普通",
            Season.WINTER, "§c枯萎"
    );

    public CropLoreApplier(ConfigManager configManager, SeasonManager seasonManager) {
        this.configManager = configManager;
        this.seasonManager = seasonManager;
    }

    /**
     * 为物品注入季节 Lore
     */
    public ItemStack applyLore(ItemStack item) {
        if (!configManager.isLoreEnabled()) return item;
        if (item == null || !item.hasItemMeta()) return item;

        Material type = item.getType();
        // 只处理种子/作物类物品
        if (!isSeedOrCrop(type)) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = new ArrayList<>();
        Season current = seasonManager.getCurrentSeason();

        lore.add("§7━━━ 季节适配 ━━━");
        for (Season season : Season.values()) {
            String rating = GROWTH_RATINGS.get(season);
            String prefix = (season == current) ? "§f» " : "  ";
            String suffix = (season == current) ? " §7← 当前" : "";
            lore.add(prefix + season.getColoredName() + "§7: " + rating + suffix);
        }
        lore.add("§7━━━━━━━━━━━━");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 判断是否是种子或作物物品
     */
    private boolean isSeedOrCrop(Material material) {
        return switch (material) {
            case WHEAT_SEEDS, CARROT, POTATO, BEETROOT_SEEDS,
                 NETHER_WART, COCOA_BEANS, SWEET_BERRIES,
                 WHEAT, BEETROOT -> true;
            default -> {
                // 尝试匹配 1.21 新增
                try {
                    Material torchflower = Material.valueOf("TORCHFLOWER_SEEDS");
                    Material pitcherPod = Material.valueOf("PITCHER_POD");
                    yield material == torchflower || material == pitcherPod;
                } catch (IllegalArgumentException e) {
                    yield false;
                }
            }
        };
    }
}
