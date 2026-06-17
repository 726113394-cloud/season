package io.Sriptirc_wp_1258.seasonalplugin;

import io.Sriptirc_wp_1258.seasonalplugin.command.SeasonAdminCommand;
import io.Sriptirc_wp_1258.seasonalplugin.command.SeasonCommand;
import io.Sriptirc_wp_1258.seasonalplugin.config.ConfigManager;
import io.Sriptirc_wp_1258.seasonalplugin.crop.CropListener;
import io.Sriptirc_wp_1258.seasonalplugin.crop.CropLoreApplier;
import io.Sriptirc_wp_1258.seasonalplugin.mechanic.FishingBoostListener;
import io.Sriptirc_wp_1258.seasonalplugin.mechanic.FreezeListener;
import io.Sriptirc_wp_1258.seasonalplugin.mechanic.HarvestBoostListener;
import io.Sriptirc_wp_1258.seasonalplugin.mechanic.HeatAreaManager;
import io.Sriptirc_wp_1258.seasonalplugin.mechanic.LeafColorManager;
import io.Sriptirc_wp_1258.seasonalplugin.season.SeasonManager;
import io.Sriptirc_wp_1258.seasonalplugin.season.SeasonScheduler;
import io.Sriptirc_wp_1258.seasonalplugin.season.SeasonTipManager;
import io.Sriptirc_wp_1258.seasonalplugin.weather.WeatherManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class SeasonalPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private SeasonManager seasonManager;
    private WeatherManager weatherManager;
    private CropLoreApplier loreApplier;
    private FreezeListener freezeListener;
    private SeasonTipManager tipManager;
    private LeafColorManager leafColorManager;
    private HeatAreaManager heatAreaManager;
    private Economy economy;

    @Override
    public void onEnable() {
        try {
            // 配置管理（最先加载）
            this.configManager = new ConfigManager(this);

            // 季节管理
            this.seasonManager = new SeasonManager(this, configManager);
            new SeasonScheduler(this, seasonManager).start();

            // 天气管理
            this.weatherManager = new WeatherManager(this, configManager, seasonManager);
            weatherManager.start();

            // Lore 注入器
            this.loreApplier = new CropLoreApplier(configManager, seasonManager);

            // Vault 经济对接
            if (!setupEconomy()) {
                getLogger().warning("§e未找到 Vault 经济插件，供暖区域计费功能将不可用");
            }

            // 供暖区域管理
            this.heatAreaManager = new HeatAreaManager(this);
            if (economy != null) heatAreaManager.setEconomy(economy);
            heatAreaManager.startParticleTask();
            heatAreaManager.createSpawnArea();

            // 注册监听器
            getServer().getPluginManager().registerEvents(
                    new CropListener(this, configManager, seasonManager, loreApplier), this);
            getServer().getPluginManager().registerEvents(
                    new HarvestBoostListener(configManager, seasonManager), this);
            getServer().getPluginManager().registerEvents(
                    new FishingBoostListener(configManager, seasonManager), this);

            this.freezeListener = new FreezeListener(this, configManager, seasonManager);
            freezeListener.setHeatAreaManager(heatAreaManager);
            getServer().getPluginManager().registerEvents(freezeListener, this);
            freezeListener.startFreezeTask();

            // 季节提示管理器（上线提示 + 每日天亮提示）
            this.tipManager = new SeasonTipManager(this, seasonManager, configManager);
            getServer().getPluginManager().registerEvents(tipManager, this);
            tipManager.startDailyTipTask();

            // 树叶季节颜色（biome 切换 + 樱花替换）
            this.leafColorManager = new LeafColorManager(this, seasonManager, configManager);
            getServer().getPluginManager().registerEvents(leafColorManager, this);
            leafColorManager.startBiomeTask();

            // 注册命令
            getCommand("season").setExecutor(new SeasonCommand(seasonManager));
            SeasonAdminCommand adminCmd = new SeasonAdminCommand(this, seasonManager, weatherManager);
            adminCmd.setLeafColorManager(leafColorManager);
            adminCmd.setHeatAreaManager(heatAreaManager);
            getServer().getPluginManager().registerEvents(adminCmd, this);
            getCommand("seasonadmin").setExecutor(adminCmd);
            getCommand("seasonadmin").setTabCompleter(adminCmd);

            getLogger().info("§a四季插件已加载！当前季节: " + seasonManager.getCurrentSeason().getColoredName());
        } catch (Exception e) {
            getLogger().severe("§c四季插件加载失败！");
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("四季插件已卸载");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }
}
