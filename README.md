# SeasonalPlugin - 四季插件

一个让 Minecraft 世界拥有四季更替的 Bukkit/Spigot 插件（1.21.x）。

## 功能

- **四季更替** — 春 → 夏 → 秋 → 冬 循环，支持现实时间或游戏天数
- **天气影响** — 每个季节有不同的下雨/雷暴概率（春天多雨，夏天晴朗）
- **冬季下雪** — 冬季通过切换生物群系为雪地类，让原版自动下雨变下雪
- **树叶颜色** — 通过切换生物群系改变树叶/草地颜色：春翠绿 → 夏原色 → 秋橙黄 → 冬银白
- **春季开花** — 春季中期（第34%~66%）橡树自动变为樱花树，破坏掉落橡树树叶；可用命令强制开花持续到夏季
- **作物季节限制** — 不同作物只能在特定季节生长（如西瓜冬季、土豆春冬），不符合季节的种不下去或长不大
- **作物生长速度** — 春季生长加速，冬季几乎停止生长
- **作物 Lore** — 种子/作物物品上显示四季适配表，当前季节高亮
- **秋季收获翻倍** — 秋季收割成熟作物时掉落物翻倍（倍率可配置）
- **春/夏钓鱼加成** — 鱼类掉落翻倍 + 钓鱼速度加快
- **冬季冻伤** — 没穿靴子和护腿的玩家在户外会缓慢掉血 + 获得缓慢效果
- **每日提示** — 玩家上线、每天天亮时提示当前季节、第几天、适合种什么
- **作物季节表** — `/season crops` 查看所有作物的四季生长适配表

## 命令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/season` (`/s`) | `seasonalplugin.use` | 查看当前季节详细报告（进度、推荐作物、小贴士） |
| `/season crops` | `seasonalplugin.use` | 查看所有作物的四季生长适配表 |
| `/seasonadmin set <季节>` | `seasonalplugin.admin` | 手动设置季节（spring/summer/autumn/winter） |
| `/seasonadmin reload` | `seasonalplugin.admin` | 重载配置文件 |
| `/seasonadmin leafrefresh` | `seasonalplugin.admin` | 强制刷新树叶颜色和 biome |
| `/seasonadmin blossom` | `seasonalplugin.admin` | 强制进入花期（仅春季可用），橡树→樱花，持续到夏季 |
| `/seasonadmin blossom off` | `seasonalplugin.admin` | 强制结束花期，樱花→橡树 |

## 作物季节适配表（默认）

| 作物 | 春 | 夏 | 秋 | 冬 |
|------|:--:|:--:|:--:|:--:|
| 小麦 | ✅ | ✅ | ✅ | ❌ |
| 胡萝卜 | ✅ | ✅ | ✅ | ❌ |
| 土豆 | ✅ | ❌ | ❌ | ✅ |
| 甜菜根 | ✅ | ❌ | ❌ | ✅ |
| 可可豆 | ✅ | ✅ | ❌ | ❌ |
| 浆果 | ✅ | ✅ | ✅ | ❌ |
| 西瓜/南瓜 | ❌ | ❌ | ❌ | ✅ |
| 火把花 | ✅ | ❌ | ❌ | ❌ |
| 瓶子草 | ✅ | ✅ | ❌ | ❌ |
| 下界疣/蘑菇 | ✅ | ✅ | ✅ | ✅ |

## 配置

详见 `config.yml`，所有数值均可自定义。

主要配置项：
- `season-cycle.use-real-time` — 换季方式（true=现实时间，false=游戏天数）
- `season-cycle.real-time-days-per-season` — 现实天数换季间隔
- `weather.*-chance` — 各季节下雨/雷暴概率
- `crop-growth.*-multiplier` — 各季节作物生长倍率
- `harvest-boost.multiplier` — 秋季收获倍率
- `fishing-boost.*` — 钓鱼加成设置
- `freeze.*` — 冻伤机制设置
- `leaf-particles.enabled` — 树叶季节颜色开关

## 使用方式

1. 导出项目为 `.sirc` 文件
2. 放入 `plugins/ScriptIrc/scripts/src/`
3. 执行 `/scriptirc compiler SeasonalPlugin`
4. 编译成功后重启服务器或 `/reload confirm`
