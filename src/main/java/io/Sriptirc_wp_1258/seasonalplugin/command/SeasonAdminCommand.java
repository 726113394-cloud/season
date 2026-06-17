package io.Sriptirc_wp_1258.seasonalplugin.command;

import io.Sriptirc_wp_1258.seasonalplugin.SeasonalPlugin;
import io.Sriptirc_wp_1258.seasonalplugin.mechanic.HeatAreaManager;
import io.Sriptirc_wp_1258.seasonalplugin.mechanic.LeafColorManager;
import io.Sriptirc_wp_1258.seasonalplugin.season.Season;
import io.Sriptirc_wp_1258.seasonalplugin.season.SeasonManager;
import io.Sriptirc_wp_1258.seasonalplugin.weather.WeatherManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /seasonadmin 命令 - 管理季节 + 供暖区域
 * 子命令: set, reload, leafrefresh, blossom, heat
 */
public class SeasonAdminCommand implements CommandExecutor, TabCompleter, Listener {

    private final SeasonalPlugin plugin;
    private final SeasonManager seasonManager;
    private final WeatherManager weatherManager;
    private LeafColorManager leafColorManager;
    private HeatAreaManager heatAreaManager;

    public SeasonAdminCommand(SeasonalPlugin plugin, SeasonManager seasonManager, WeatherManager weatherManager) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
        this.weatherManager = weatherManager;
    }

    public void setLeafColorManager(LeafColorManager leafColorManager) {
        this.leafColorManager = leafColorManager;
    }

    public void setHeatAreaManager(HeatAreaManager heatAreaManager) {
        this.heatAreaManager = heatAreaManager;
    }

    /**
     * 木斧选点监听
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (heatAreaManager == null) return;
        Player player = event.getPlayer();

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.BLAZE_ROD) return;
        if (!player.hasPermission("seasonalplugin.admin")) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            heatAreaManager.setPos1(player, event.getClickedBlock().getLocation());
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            heatAreaManager.setPos2(player, event.getClickedBlock().getLocation());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("seasonalplugin.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6用法: /seasonadmin set <季节> | reload | leafrefresh | blossom | heat");
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
            case "heat" -> {
                handleHeatCommand(sender, args);
            }
            default -> {
                sender.sendMessage("§c未知子命令，可用: set, reload, leafrefresh, blossom, heat");
            }
        }

        return true;
    }

    private void handleHeatCommand(CommandSender sender, String[] args) {
        if (heatAreaManager == null) {
            sender.sendMessage("§c供暖区域管理器未就绪");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§6供暖区域命令:");
            sender.sendMessage("§e/seasonadmin heat wand §7- 获取木斧选点");
            sender.sendMessage("§e/seasonadmin heat create §7- 根据选点创建区域");
            sender.sendMessage("§e/seasonadmin heat set <世界> <x1> <y1> <z1> <x2> <y2> <z2> §7- 直接设置区域");
            sender.sendMessage("§e/seasonadmin heat list §7- 列出所有区域");
            sender.sendMessage("§e/seasonadmin heat remove <编号> §7- 删除指定区域");
            sender.sendMessage("§e/seasonadmin heat clear §7- 清除所有区域");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "wand" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§c控制台无法使用此命令");
                    return;
                }
                player.getInventory().addItem(new ItemStack(Material.BLAZE_ROD));
                player.sendMessage("§a已获得烈焰棒！左键点1，右键点2 选择供暖区域");
            }
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§c控制台请使用 heat set 命令");
                    return;
                }
                String err = heatAreaManager.tryCreateArea(player);
                if (err != null) {
                    player.sendMessage(err);
                }
            }
            case "set" -> {
                if (args.length < 9) {
                    sender.sendMessage("§c用法: /seasonadmin heat set <世界> <x1> <y1> <z1> <x2> <y2> <z2>");
                    return;
                }
                try {
                    String world = args[2];
                    int x1 = Integer.parseInt(args[3]);
                    int y1 = Integer.parseInt(args[4]);
                    int z1 = Integer.parseInt(args[5]);
                    int x2 = Integer.parseInt(args[6]);
                    int y2 = Integer.parseInt(args[7]);
                    int z2 = Integer.parseInt(args[8]);
                    if (heatAreaManager.createArea(world, x1, y1, z1, x2, y2, z2)) {
                        sender.sendMessage("§a已创建供暖区域！");
                    } else {
                        sender.sendMessage("§c世界不存在！");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c坐标必须是整数！");
                }
            }
            case "list" -> {
                List<String> areas = heatAreaManager.listAreas();
                if (areas.isEmpty()) {
                    sender.sendMessage("§7暂无供暖区域");
                } else {
                    sender.sendMessage("§6供暖区域列表:");
                    for (String s : areas) sender.sendMessage(s);
                }
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage("§c用法: /seasonadmin heat remove <编号>");
                    return;
                }
                try {
                    int index = Integer.parseInt(args[2]);
                    if (sender instanceof Player player) {
                        if (heatAreaManager.removeAreaAndRefund(index, player)) {
                            // 提示已经在 removeAreaAndRefund 里发了
                        } else {
                            sender.sendMessage("§c无效编号或无法删除默认区域");
                        }
                    } else {
                        if (heatAreaManager.removeArea(index)) {
                            sender.sendMessage("§a已删除 #" + index + " 号供暖区域");
                        } else {
                            sender.sendMessage("§c无效编号");
                        }
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c编号必须是数字");
                }
            }
            case "clear" -> {
                heatAreaManager.clearAll();
                sender.sendMessage("§a已清除所有供暖区域");
            }
            default -> sender.sendMessage("§c未知子命令，可用: wand, create, set, list, remove, clear");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("seasonalplugin.admin")) return new ArrayList<>();

        if (args.length == 1) {
            return Arrays.asList("set", "reload", "leafrefresh", "blossom", "heat").stream()
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

        if (args.length == 2 && args[0].equalsIgnoreCase("heat")) {
            return Arrays.asList("wand", "create", "set", "list", "remove", "clear").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
