package io.Sriptirc_wp_1258.seasonalplugin.mechanic;

import io.Sriptirc_wp_1258.seasonalplugin.SeasonalPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * 供暖区域管理器
 * 在选定的立方体区域内，玩家不会受到冻伤
 * 支持烈焰棒选点 + 命令设置，持久化到文件
 * 设置时每方块 10 游戏币，删除时返还每方块 4 游戏币
 */
public class HeatAreaManager {

    private final SeasonalPlugin plugin;
    private Economy economy;

    // 计费标准
    private static final double COST_PER_BLOCK = 10.0;
    private static final double REFUND_PER_BLOCK = 4.0;

    // 供暖区域列表
    private final List<HeatArea> heatAreas = new ArrayList<>();

    // 玩家选点缓存
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    // 默认出生点区域是否已创建
    private boolean spawnAreaCreated = false;

    public HeatAreaManager(SeasonalPlugin plugin) {
        this.plugin = plugin;
        loadAreas();
    }

    public void setEconomy(Economy economy) {
        this.economy = economy;
    }

    /**
     * 创建默认出生点供暖区域（21×21，以出生点为中心半径10格）
     */
    public void createSpawnArea() {
        if (spawnAreaCreated) return;

        for (World world : plugin.getServer().getWorlds()) {
            if (!world.hasSkyLight()) continue;
            Location spawn = world.getSpawnLocation();
            int sx = spawn.getBlockX();
            int sy = spawn.getBlockY();
            int sz = spawn.getBlockZ();

            // 检查是否已经存在包含出生点的区域
            boolean alreadyCovered = false;
            for (HeatArea area : heatAreas) {
                if (area.contains(spawn)) {
                    alreadyCovered = true;
                    break;
                }
            }
            if (alreadyCovered) continue;

            // 21×21 范围，y 方向从 -64 到 320（覆盖整个建筑高度）
            HeatArea area = new HeatArea(
                    world.getName(),
                    sx - 10, -64, sz - 10,
                    sx + 10, 320, sz + 10,
                    true // 标记为默认出生点区域
            );
            heatAreas.add(area);
            spawnAreaCreated = true;
            plugin.getLogger().info("§a已创建默认出生点供暖区域 (21×21)");
        }
    }

    // ==================== 区域数据 ====================

    public static class HeatArea {
        public final String world;
        public final int x1, y1, z1, x2, y2, z2;
        public final boolean isSpawnArea; // 是否是默认出生点区域

        public HeatArea(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
            this(world, x1, y1, z1, x2, y2, z2, false);
        }

        public HeatArea(String world, int x1, int y1, int z1, int x2, int y2, int z2, boolean isSpawnArea) {
            this.world = world;
            this.x1 = Math.min(x1, x2);
            this.y1 = Math.min(y1, y2);
            this.z1 = Math.min(z1, z2);
            this.x2 = Math.max(x1, x2);
            this.y2 = Math.max(y1, y2);
            this.z2 = Math.max(z1, z2);
            this.isSpawnArea = isSpawnArea;
        }

        public boolean contains(Location loc) {
            if (!loc.getWorld().getName().equals(world)) return false;
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
        }

        /**
         * 计算区域内的方块总数
         */
        public int getBlockCount() {
            long dx = (long) x2 - x1 + 1;
            long dy = (long) y2 - y1 + 1;
            long dz = (long) z2 - z1 + 1;
            long total = dx * dy * dz;
            // 防止溢出，限制最大返回 Integer.MAX_VALUE
            return (int) Math.min(total, Integer.MAX_VALUE);
        }
    }

    // ==================== 计费 ====================

    /**
     * 尝试扣费创建区域
     * @return null 表示成功，非 null 为失败原因
     */
    public String tryCreateArea(Player player) {
        Location p1 = pos1.get(player.getUniqueId());
        Location p2 = pos2.get(player.getUniqueId());

        if (p1 == null || p2 == null) return "§c请先用烈焰棒选择两个点！";
        if (!p1.getWorld().getName().equals(p2.getWorld().getName())) return "§c两个点必须在同一个世界！";

        // 计算方块数
        HeatArea temp = new HeatArea(p1.getWorld().getName(),
                p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(),
                p2.getBlockX(), p2.getBlockY(), p2.getBlockZ());
        int blocks = temp.getBlockCount();
        double cost = blocks * COST_PER_BLOCK;

        // 扣费
        if (economy != null) {
            if (!economy.has(player, cost)) {
                double balance = economy.getBalance(player);
                return "§c余额不足！需要 §e" + cost + " §c游戏币，你只有 §e" + balance + " §c游戏币\n" +
                        "§7（每方块 " + COST_PER_BLOCK + " 游戏币，共 " + blocks + " 方块）";
            }
            economy.withdrawPlayer(player, cost);
        }

        HeatArea area = new HeatArea(p1.getWorld().getName(),
                p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(),
                p2.getBlockX(), p2.getBlockY(), p2.getBlockZ());
        heatAreas.add(area);
        saveAreas();

        player.sendMessage("§a已创建供暖区域！");
        player.sendMessage("§7区域大小: " + blocks + " 方块，花费: §e" + cost + " §7游戏币");
        return null;
    }

    /**
     * 删除区域并返还游戏币
     * @return true 表示成功
     */
    public boolean removeAreaAndRefund(int index, Player player) {
        if (index < 0 || index >= heatAreas.size()) return false;

        HeatArea area = heatAreas.get(index);
        if (area.isSpawnArea) {
            player.sendMessage("§c默认出生点区域无法删除！");
            return false;
        }

        int blocks = area.getBlockCount();
        double refund = blocks * REFUND_PER_BLOCK;

        heatAreas.remove(index);
        saveAreas();

        if (economy != null && refund > 0) {
            economy.depositPlayer(player, refund);
            player.sendMessage("§a已删除供暖区域，返还 §e" + refund + " §a游戏币（每方块 " + REFUND_PER_BLOCK + "）");
        } else {
            player.sendMessage("§a已删除供暖区域");
        }
        return true;
    }

    // ==================== 公开方法 ====================

    public boolean isInHeatArea(Location loc) {
        for (HeatArea area : heatAreas) {
            if (area.contains(loc)) return true;
        }
        return false;
    }

    public void setPos1(Player player, Location loc) {
        pos1.put(player.getUniqueId(), loc);
        player.sendMessage("§a已设置供暖区域点1: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
    }

    public void setPos2(Player player, Location loc) {
        pos2.put(player.getUniqueId(), loc);
        player.sendMessage("§a已设置供暖区域点2: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
    }

    public boolean createAreaFromSelection(Player player) {
        Location p1 = pos1.get(player.getUniqueId());
        Location p2 = pos2.get(player.getUniqueId());
        if (p1 == null || p2 == null) return false;
        if (!p1.getWorld().getName().equals(p2.getWorld().getName())) return false;

        HeatArea area = new HeatArea(p1.getWorld().getName(),
                p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(),
                p2.getBlockX(), p2.getBlockY(), p2.getBlockZ());
        heatAreas.add(area);
        saveAreas();
        return true;
    }

    public boolean createArea(String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) return false;
        heatAreas.add(new HeatArea(worldName, x1, y1, z1, x2, y2, z2));
        saveAreas();
        return true;
    }

    public List<String> listAreas() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < heatAreas.size(); i++) {
            HeatArea a = heatAreas.get(i);
            String tag = a.isSpawnArea ? " §7[默认出生点]" : "";
            result.add("§6#" + i + tag + " §7" + a.world + " §f(" + a.x1 + "," + a.y1 + "," + a.z1
                    + ") -> (" + a.x2 + "," + a.y2 + "," + a.z2 + ")"
                    + " §7(" + a.getBlockCount() + " 方块)");
        }
        return result;
    }

    public boolean removeArea(int index) {
        if (index < 0 || index >= heatAreas.size()) return false;
        if (heatAreas.get(index).isSpawnArea) return false;
        heatAreas.remove(index);
        saveAreas();
        return true;
    }

    public void clearAll() {
        heatAreas.removeIf(a -> !a.isSpawnArea);
        saveAreas();
    }

    // ==================== 粒子显示 ====================

    public void startParticleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (HeatArea area : heatAreas) {
                    showAreaParticles(area);
                }
            }
        }.runTaskTimer(plugin, 60L, 100L);
    }

    private void showAreaParticles(HeatArea area) {
        World world = plugin.getServer().getWorld(area.world);
        if (world == null) return;

        boolean hasPlayers = false;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(new Location(world, area.x1, area.y1, area.z1)) < 10000) {
                hasPlayers = true;
                break;
            }
        }
        if (!hasPlayers) return;

        Particle.DustOptions dust = new Particle.DustOptions(
                org.bukkit.Color.fromRGB(255, 100, 50), 1.5f);

        int[][] edges = {
                {area.x1, area.y1, area.z1, area.x2, area.y1, area.z1},
                {area.x1, area.y1, area.z1, area.x1, area.y2, area.z1},
                {area.x1, area.y1, area.z1, area.x1, area.y1, area.z2},
                {area.x2, area.y1, area.z1, area.x2, area.y2, area.z1},
                {area.x2, area.y1, area.z1, area.x2, area.y1, area.z2},
                {area.x1, area.y2, area.z1, area.x2, area.y2, area.z1},
                {area.x1, area.y2, area.z1, area.x1, area.y2, area.z2},
                {area.x2, area.y2, area.z1, area.x2, area.y2, area.z2},
                {area.x1, area.y1, area.z2, area.x2, area.y1, area.z2},
                {area.x1, area.y1, area.z2, area.x1, area.y2, area.z2},
                {area.x2, area.y1, area.z2, area.x2, area.y2, area.z2},
                {area.x1, area.y2, area.z2, area.x2, area.y2, area.z2}
        };

        for (int[] edge : edges) {
            drawLine(world, edge[0], edge[1], edge[2], edge[3], edge[4], edge[5], dust);
        }
    }

    private void drawLine(World world, int x1, int y1, int z1, int x2, int y2, int z2, Particle.DustOptions dust) {
        int dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i += 2) {
            double t = (double) i / steps;
            world.spawnParticle(Particle.DUST, x1 + dx * t, y1 + dy * t, z1 + dz * t, 1, 0, 0, 0, 0, dust);
        }
    }

    // ==================== 持久化 ====================

    private File getDataFile() {
        return new File(plugin.getDataFolder(), "heatareas.yml");
    }

    private void saveAreas() {
        File file = getDataFile();
        YamlConfiguration data = new YamlConfiguration();
        data.set("spawn-area-created", spawnAreaCreated);

        for (int i = 0; i < heatAreas.size(); i++) {
            HeatArea a = heatAreas.get(i);
            String path = "areas." + i;
            data.set(path + ".world", a.world);
            data.set(path + ".x1", a.x1);
            data.set(path + ".y1", a.y1);
            data.set(path + ".z1", a.z1);
            data.set(path + ".x2", a.x2);
            data.set(path + ".y2", a.y2);
            data.set(path + ".z2", a.z2);
            data.set(path + ".is-spawn-area", a.isSpawnArea);
        }

        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "无法保存供暖区域数据", e);
        }
    }

    private void loadAreas() {
        File file = getDataFile();
        if (!file.exists()) return;

        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        spawnAreaCreated = data.getBoolean("spawn-area-created", false);

        if (!data.contains("areas")) return;

        for (String key : data.getConfigurationSection("areas").getKeys(false)) {
            String path = "areas." + key;
            String world = data.getString(path + ".world");
            int x1 = data.getInt(path + ".x1");
            int y1 = data.getInt(path + ".y1");
            int z1 = data.getInt(path + ".z1");
            int x2 = data.getInt(path + ".x2");
            int y2 = data.getInt(path + ".y2");
            int z2 = data.getInt(path + ".z2");
            boolean isSpawn = data.getBoolean(path + ".is-spawn-area", false);

            heatAreas.add(new HeatArea(world, x1, y1, z1, x2, y2, z2, isSpawn));
        }

        plugin.getLogger().info("§a已加载 " + heatAreas.size() + " 个供暖区域");
    }
}
