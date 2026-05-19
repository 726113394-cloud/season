package io.Sriptirc_wp_1258.seasonalplugin.command;

import io.Sriptirc_wp_1258.seasonalplugin.SeasonalPlugin;
import io.Sriptirc_wp_1258.seasonalplugin.mechanic.LeafColorManager;
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
    private LeafColorManager leafColorManager;

    public SeasonAdminCommand(SeasonalPlugin plugin, SeasonManager seasonManager, WeatherManager weatherManager) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
        this.weatherManager = weatherManager;
    }

    public void setLeafColorManager(LeafColorManager leafColorManager) {
        this.leafColorManager = leafColorManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("seasonalplugin.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6用法: /seasonadmin set <季节> | reload | leafrefresh");
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
                    sender.sendMessage("§7树叶和biome将在30秒内自动更新");
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§c无效季节，可用: spring, summer, autumn, winter");
                }
            }
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage("§a配置已重载");
            }
            case "leafrefresh" -> {
                if (leafColorManager != null) {
                    leafColorManager.forceRefresh();
                    sender.sendMessage("§a树叶/biome已强制刷新！");
                } else {
                    sender.sendMessage("§c树叶管理器未就绪");
                }
            }
            case "blossom" -> {
                if (leafColorManager != null) {
                    if (args.length > 1 && args[1].equalsIgnoreCase("off")) {
                        leafColorManager.forceBlossomEnd();
                        sender.sendMessage("§a已强制结束花期，樱花→橡树");
                    } else {
                        boolean success = leafColorManager.forceBlossomStart();
                        if (success) {
                            sender.sendMessage("§d🌸 已强制进入花期！橡树→樱花，将持续到夏季");
                        } else {
                            sender.sendMessage("§c当前不是春季，无法进入花期！");
                        }
                    }
                } else {
                    sender.sendMessage("§c树叶管理器未就绪");
                }
            }
            default -> {
                sender.sendMessage("§c未知子命令，可用: set, reload, leafrefresh, blossom");
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("seasonalplugin.admin")) return new ArrayList<>();

        if (args.length == 1) {
            return Arrays.asList("set", "reload", "leafrefresh", "blossom").stream()
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

        if (args.length == 2 && args[0].equalsIgnoreCase("blossom")) {
            return Arrays.asList("off").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
