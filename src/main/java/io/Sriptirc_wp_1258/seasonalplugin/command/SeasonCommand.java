package io.Sriptirc_wp_1258.seasonalplugin.command;

import io.Sriptirc_wp_1258.seasonalplugin.crop.CropRegistry;
import io.Sriptirc_wp_1258.seasonalplugin.season.Season;
import io.Sriptirc_wp_1258.seasonalplugin.season.SeasonManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /season 命令 - 查看当前季节信息 + 作物季节适配表
 * 子命令: /season crops - 查看所有作物的四季生长情况
 */
public class SeasonCommand implements CommandExecutor, TabCompleter {

    private final SeasonManager seasonManager;

    // 四季的图标
    private static final String SPRING_ICON = "§a❀";
    private static final String SUMMER_ICON = "§e☀";
    private static final String AUTUMN_ICON = "§6☂";
    private static final String WINTER_ICON = "§b❄";

    // 生长评级
    private static final String CAN_GROW = "§a✔";
    private static final String CANT_GROW = "§c✘";

    public SeasonCommand(SeasonManager seasonManager) {
        this.seasonManager = seasonManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /season crops - 查看作物季节表
        if (args.length > 0 && args[0].equalsIgnoreCase("crops")) {
            showCropTable(sender);
            return true;
        }

        // /season - 默认显示季节报告
        Season current = seasonManager.getCurrentSeason();
        int day = seasonManager.getDayOfSeason();
        int total = seasonManager.getTotalDaysInSeason();
        String remaining = seasonManager.getTimeRemaining();

        sender.sendMessage("§6━━━ 季节详细报告 ━━━");
        sender.sendMessage("§e当前季节: " + current.getColoredName());
        sender.sendMessage("§e季节进度: §f第 " + day + " / " + total + " 天");
        sender.sendMessage("§e距下次换季: §f" + remaining);

        // 适合种植的作物
        List<String> recommended = CropRegistry.getRecommendedCrops(current);
        if (!recommended.isEmpty()) {
            sender.sendMessage("§e适合种植: §a" + String.join("§7, §a", recommended));
        }

        // 季节小贴士
        String tip = getSeasonTip(current);
        if (tip != null) {
            sender.sendMessage("§e小贴士: §f" + tip);
        }

        sender.sendMessage("§7提示: §f/season crops §7查看完整作物季节表");
        sender.sendMessage("§6━━━━━━━━━━━━");

        return true;
    }

    /**
     * 显示所有作物的四季生长情况表
     */
    private void showCropTable(CommandSender sender) {
        Season current = seasonManager.getCurrentSeason();

        sender.sendMessage("§6╔══════════════════════════════════════╗");
        sender.sendMessage("§6║ §e作物四季生长适配表 " + SPRING_ICON + " " + SUMMER_ICON + " " + AUTUMN_ICON + " " + WINTER_ICON + "      §6║");
        sender.sendMessage("§6╠══════════════════════════════════════╣");
        sender.sendMessage("§6║  " + SPRING_ICON + " 春  " + SUMMER_ICON + " 夏  " + AUTUMN_ICON + " 秋  " + WINTER_ICON + " 冬           §6║");
        sender.sendMessage("§6╠══════════════════════════════════════╣");

        // 遍历所有受季节影响的作物
        List<Material> crops = CropRegistry.getAllSeasonRestrictedCrops();
        for (Material crop : crops) {
            String name = CropRegistry.formatCropName(crop);
            Set<Season> allowed = CropRegistry.getAllowedSeasons(crop);

            String spring = allowed.contains(Season.SPRING) ? CAN_GROW : CANT_GROW;
            String summer = allowed.contains(Season.SUMMER) ? CAN_GROW : CANT_GROW;
            String autumn = allowed.contains(Season.AUTUMN) ? CAN_GROW : CANT_GROW;
            String winter = allowed.contains(Season.WINTER) ? CAN_GROW : CANT_GROW;

            // 当前季节高亮
            String highlight = allowed.contains(current) ? " §a← 推荐" : "";

            sender.sendMessage("§6║ §f" + padRight(name, 8)
                    + " " + spring + "  " + summer + "  " + autumn + "  " + winter
                    + highlight + " §6║");
        }

        // 不受季节影响的作物
        sender.sendMessage("§6╠══════════════════════════════════════╣");
        sender.sendMessage("§6║ §7不受季节影响: 下界疣, 蘑菇类      §6║");
        sender.sendMessage("§6╚══════════════════════════════════════╝");

        sender.sendMessage("§7" + CAN_GROW + " = 可生长  " + CANT_GROW + " = 无法生长");
    }

    /**
     * 中文字符串右补空格对齐
     */
    private String padRight(String text, int length) {
        // 中文算 2 个宽度
        int actualLen = 0;
        for (char c : text.toCharArray()) {
            actualLen += (c >= 0x4E00 && c <= 0x9FFF) ? 2 : 1;
        }
        int padding = length - actualLen;
        if (padding < 0) padding = 0;
        return text + " ".repeat(padding);
    }

    private String getSeasonTip(Season season) {
        return switch (season) {
            case SPRING -> "万物复苏，作物生长速度最快！钓鱼也有加成哦~";
            case SUMMER -> "天气炎热，适合种可可豆和下界疣，钓鱼依然有加成！";
            case AUTUMN -> "收获的季节！收割作物有额外掉落！抓紧囤粮过冬~";
            case WINTER -> "天寒地冻，记得穿好靴子和护腿！大部分作物无法生长。";
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("crops").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
