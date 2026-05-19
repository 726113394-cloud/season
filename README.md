# SeasonalPlugin - 四季插件

一个让 Minecraft 世界拥有四季更替的 Bukkit/Spigot 插件（1.21.x）。

## 功能

- **四季更替** — 春 → 夏 → 秋 → 冬 循环，支持现实时间或游戏天数
- **天气影响** — 每个季节有不同的下雨/雷暴概率（春天多雨，夏天晴朗）
- **作物生长** — 春季生长加速，冬季几乎停止生长
- **作物 Lore** — 种子/作物物品上显示四季适配表，当前季节高亮
- **秋季收获翻倍** — 秋季收割成熟作物时掉落物翻倍（倍率可配置）
- **春/夏钓鱼加成** — 鱼类掉落翻倍 + 钓鱼速度加快
- **冬季冻伤** — 没穿靴子和护腿的玩家在户外会缓慢掉血 + 获得缓慢效果

## 命令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/season` (`/s`) | `seasonalplugin.use` | 查看当前季节和剩余换季时间 |
| `/seasonadmin set <季节>` | `seasonalplugin.admin` | 手动设置季节（spring/summer/autumn/winter） |
| `/seasonadmin reload` | `seasonalplugin.admin` | 重载配置文件 |

## 配置

详见 `config.yml`，所有数值均可自定义。

主要配置项：
- `season-cycle.use-real-time` — 换季方式（true=现实时间，false=游戏天数）
- `season-cycle.real-time-days-per-season` — 现实天数换季间隔
- `crop-growth.*-multiplier` — 各季节作物生长倍率
- `harvest-boost.multiplier` — 秋季收获倍率
- `fishing-boost.*` — 钓鱼加成设置
- `freeze.*` — 冻伤机制设置

## 使用方式

1. 导出项目为 `.sirc` 文件
2. 放入 `plugins/ScriptIrc/scripts/src/`
3. 执行 `/scriptirc compiler SeasonalPlugin`
4. 编译成功后重启服务器或 `/reload confirm`
