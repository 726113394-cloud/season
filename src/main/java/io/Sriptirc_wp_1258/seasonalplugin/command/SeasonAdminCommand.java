package io.Sriptirc_wp_1258.seasonalplugin.command;

import io.Sriptirc_wp_1258.seasonalplugin.SeasonalPlugin;
import io.Sriptirc_wp_1258.seasonalplugin.season.Season;
import io.Sriptirc_wp_1258.seasonalplugin.season.SeasonManager;
import io.Sriptirc_wp_1258.seasonalplugin.weather.WeatherManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /seasonadmin 命令 - 管理季节
 * 子命令: set <spring/summer/autumn/winter>, reload
 */
public class SeasonAdminCommand implements CommandExecutor, TabCompleter {

    private final SeasonalPlugin plugin;
    private final SeasonManager seasonManager;
    private final WeatherManager weatherManager;

    public SeasonAdminCommand(SeasonalPlugin plugin, SeasonManager seasonManager, WeatherManager weatherManager) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
        this.weatherManager = weatherManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("seasonalplugin.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6用法: /seasonadmin set <季节> | /seasonadmin reload");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set" -> {
                if (args.length < 2) {
                    sender.sendMessage("§c请指定季节: spring, summer, autumn, winter");
                    return true;
                }
                try {
                    Season season = Season.valueOf(args[1].toUpperCase());
                    seasonManager.setSeason(season);
                    weatherManager.refreshWeather();
                    sender.sendMessage("§a季节已设置为 " + season.getColoredName());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§c无效季节，可用: spring, summer, autumn, winter");
                }
            }
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage("§a配置已重载");
            }
            default -> {
                sender.sendMessage("§c未知子命令，可用: set, reload");
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("seasonalplugin.admin")) return new ArrayList<>();

        if (args.length == 1) {
            return Arrays.asList("set", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return Arrays.stream(Season.values())
                    .map(Enum::name)
                    .map(String::toLowerCase)
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
