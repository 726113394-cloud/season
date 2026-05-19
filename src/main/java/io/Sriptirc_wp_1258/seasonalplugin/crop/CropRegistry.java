package io.Sriptirc_wp_1258.seasonalplugin.crop;

import io.Sriptirc_wp_1258.seasonalplugin.season.Season;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;

import java.util.*;

/**
 * 作物注册表 - 记录所有受季节影响的作物
 * 支持原版 + 任何实现 Ageable 的作物（兼容模组）
 * 包含作物-季节适配表
 */
public class CropRegistry {

    private static final Set<Material> CROP_MATERIALS = new HashSet<>();
    private static final Set<Material> UNIVERAL_CROPS = new HashSet<>(); // 不受季节影响的作物
    private static final Map<Material, Set<Season>> CROP_SEASON_MAP = new HashMap<>();

    static {
        // ===== 受季节影响的作物 =====
        // 小麦 - 春夏秋
        registerCrop(Material.WHEAT, Season.SPRING, Season.SUMMER, Season.AUTUMN);
        // 胡萝卜 - 春夏秋
        registerCrop(Material.CARROTS, Season.SPRING, Season.SUMMER, Season.AUTUMN);
        // 土豆 - 春、冬
        registerCrop(Material.POTATOES, Season.SPRING, Season.WINTER);
        // 甜菜根 - 春、冬
        registerCrop(Material.BEETROOTS, Season.SPRING, Season.WINTER);
        // 可可豆 - 春夏
        registerCrop(Material.COCOA, Season.SPRING, Season.SUMMER);
        // 浆果灌木 - 春夏秋
        registerCrop(Material.SWEET_BERRY_BUSH, Season.SPRING, Season.SUMMER, Season.AUTUMN);
        // 西瓜/南瓜 - 冬季
        registerCrop(Material.MELON_STEM, Season.WINTER);
        registerCrop(Material.PUMPKIN_STEM, Season.WINTER);
        // 火把花 - 仅春季
        tryRegisterCrop("TORCHFLOWER_CROP", Season.SPRING);
        // 瓶子草 - 春夏
        tryRegisterCrop("PITCHER_CROP", Season.SPRING, Season.SUMMER);

        // ===== 不受季节影响的作物 =====
        UNIVERAL_CROPS.add(Material.NETHER_WART);
        UNIVERAL_CROPS.add(Material.BROWN_MUSHROOM);
        UNIVERAL_CROPS.add(Material.RED_MUSHROOM);
        UNIVERAL_CROPS.add(Material.CRIMSON_FUNGUS);
        UNIVERAL_CROPS.add(Material.WARPED_FUNGUS);

        // 所有受季节影响的也加入总列表
        CROP_MATERIALS.addAll(CROP_SEASON_MAP.keySet());
        // 通用作物也加入总列表（用于 isCrop 判断）
        CROP_MATERIALS.addAll(UNIVERAL_CROPS);
    }

    private static void registerCrop(Material material, Season... seasons) {
        CROP_SEASON_MAP.put(material, EnumSet.copyOf(Arrays.asList(seasons)));
    }

    private static void tryRegisterCrop(String name, Season... seasons) {
        try {
            Material mat = Material.valueOf(name);
            registerCrop(mat, seasons);
        } catch (IllegalArgumentException ignored) {}
    }

    /**
     * 判断方块是否是作物（受季节影响 + 不受影响的都算）
     */
    public static boolean isCrop(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        if (CROP_MATERIALS.contains(type)) return true;
        // 兜底
        BlockData data = block.getBlockData();
        return data instanceof Ageable;
    }

    /**
     * 判断某个作物是否受季节限制（true = 受限制，false = 四季都能长）
     */
    public static boolean isSeasonRestricted(Material cropType) {
        return CROP_SEASON_MAP.containsKey(cropType);
    }

    /**
     * 判断某个作物在指定季节能否生长
     */
    public static boolean canGrowInSeason(Material cropType, Season season) {
        // 不受季节影响的 -> 啥季节都能长
        if (UNIVERAL_CROPS.contains(cropType)) return true;
        Set<Season> allowed = CROP_SEASON_MAP.get(cropType);
        return allowed != null && allowed.contains(season);
    }

    /**
     * 获取某个作物允许生长的季节列表
     */
    public static Set<Season> getAllowedSeasons(Material cropType) {
        return CROP_SEASON_MAP.getOrDefault(cropType, EnumSet.allOf(Season.class));
    }

    /**
     * 获取当前季节适合种植的所有作物（用于提示）
     */
    public static List<String> getRecommendedCrops(Season season) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<Material, Set<Season>> entry : CROP_SEASON_MAP.entrySet()) {
            if (entry.getValue().contains(season)) {
                result.add(formatCropName(entry.getKey()));
            }
        }
        return result;
    }

    /**
     * 把 Material 转成中文友好名
     */
    public static String formatCropName(Material material) {
        return switch (material) {
            case WHEAT -> "小麦";
            case CARROTS -> "胡萝卜";
            case POTATOES -> "土豆";
            case BEETROOTS -> "甜菜根";
            case NETHER_WART -> "下界疣";
            case COCOA -> "可可豆";
            case SWEET_BERRY_BUSH -> "浆果";
            case MELON_STEM -> "西瓜";
            case PUMPKIN_STEM -> "南瓜";
            default -> {
                String name = material.name().toLowerCase().replace('_', ' ');
                yield name.substring(0, 1).toUpperCase() + name.substring(1);
            }
        };
    }

    /**
     * 获取方块对应的 Material 类型（用于季节判断）
     */
    public static Material getCropType(Block block) {
        return block.getType();
    }

    /**
     * 获取作物的生长进度（0.0 ~ 1.0）
     */
    public static double getGrowthProgress(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            return (double) ageable.getAge() / (double) ageable.getMaximumAge();
        }
        return 0;
    }

    /**
     * 判断作物是否成熟
     */
    public static boolean isMature(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return false;
    }

    /**
     * 获取所有受季节限制的作物列表（按注册顺序）
     */
    public static List<Material> getAllSeasonRestrictedCrops() {
        return new ArrayList<>(CROP_SEASON_MAP.keySet());
    }

    /**
     * 获取作物对应的种子/物品
     */
    public static Material getSeedForCrop(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA -> Material.COCOA_BEANS;
            case SWEET_BERRY_BUSH -> Material.SWEET_BERRIES;
            case MELON_STEM -> Material.MELON_SEEDS;
            case PUMPKIN_STEM -> Material.PUMPKIN_SEEDS;
            default -> crop;
        };
    }
}
