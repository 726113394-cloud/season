package io.Sriptirc_wp_1258.seasonalplugin.mechanic;

import io.Sriptirc_wp_1258.seasonalplugin.config.ConfigManager;
import io.Sriptirc_wp_1258.seasonalplugin.season.Season;
import io.Sriptirc_wp_1258.seasonalplugin.season.SeasonManager;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * 春/夏季钓鱼加成
 * - 鱼类掉落翻倍
 * - 钓鱼速度加快（缩短等待时间）
 */
public class FishingBoostListener implements Listener {

    private final ConfigManager configManager;
    private final SeasonManager seasonManager;
    private final Random random = new Random();

    public FishingBoostListener(ConfigManager configManager, SeasonManager seasonManager) {
        this.configManager = configManager;
        this.seasonManager = seasonManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerFish(PlayerFishEvent event) {
        if (!configManager.isFishingBoostEnabled()) return;

        Season current = seasonManager.getCurrentSeason();
        if (current != Season.SPRING && current != Season.SUMMER) return;

        // 钓鱼速度加快
        if (configManager.isFishingSpeedBoost()) {
            FishHook hook = event.getHook();
            // 通过设置最小等待时间来实现加速
            // 原版鱼钩默认最小等待时间约 100 tick
            if (hook.getMinWaitTime() > configManager.getFishingSpeedBoostTicks()) {
                hook.setMinWaitTime(configManager.getFishingSpeedBoostTicks());
            }
        }

        // 钓到鱼时翻倍
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            if (event.getCaught() instanceof Item caught) {
                ItemStack fish = caught.getItemStack();
                if (isFish(fish)) {
                    double multiplier = configManager.getFishingBoostMultiplier();
                    int extraCount = (int) Math.floor(fish.getAmount() * (multiplier - 1.0));
                    double extraChance = (fish.getAmount() * (multiplier - 1.0)) - extraCount;

                    if (extraCount > 0) {
                        fish.setAmount(fish.getAmount() + extraCount);
                    }
                    if (extraChance > 0 && random.nextDouble() < extraChance) {
                        fish.setAmount(fish.getAmount() + 1);
                    }
                    caught.setItemStack(fish);
                }
            }
        }
    }

    /**
     * 判断是否是鱼类物品
     */
    private boolean isFish(ItemStack item) {
        return switch (item.getType()) {
            case COD, SALMON, TROPICAL_FISH, PUFFERFISH -> true;
            default -> false;
        };
    }
}
