# 雷霆战机 (Plane War)

2D 竖版飞机大战，完全在这台 Android 手机里（Termux）编译、签名、安装，不需要 PC / Android Studio / Gradle。

## 玩法

* **机头始终朝上**，屏幕任意位置**拖动**即可让飞机上下左右移动（不用点到飞机）。
* 松手后飞机滑行一小段距离停下；飞机碰到屏幕边缘会停下并贴边。
* 飞机自动开火，无需按键；战场底部两角是两枚**主动技能**按钮。
* **能量超必杀**（右下星芒按钮，充能环填满即就绪）：击杀敌机与时间都会充能，满 100 释放全屏超必杀，
  5 种随机主题：烈焰流星（三连流星爆）/ 万雷齐鸣（六道雷柱）/ 圣光普照（全场清 +1 命）/
  大地裂变（地裂波自下而上）/ 冰封万里（全场冰封 4 秒）。任何主题都会清空全屏敌弹。
* **炸弹库存**（左下炸弹按钮，下方 `xN` 是库存）：开局 1 颗，上限 3，吃炸弹道具 +1（满了就加分）；
  自己选时机引爆，清弹 + 全场伤害。
* **擦弹（graze）**：敌弹在机身外 dp(46) 的环带内掠过（未击中）→ 充能 + 连击 + 少量分数，
  同一颗弹 0.25s 内只算一次；直击不奖励（先判直击）。autopilot 会贴着环带躲（dp(70)，
  挨打后回退到 dp(104)），所以自动验收里的擦弹是真躲出来的，不是钩子刷的。
* **连击狂热**：连击 ≥ 30 进入狂热，得分 x2 + 射速提升 + 机身红色尾焰，进入时全屏横幅。
* **多阶段 BOSS**：每存活 80 秒出场一只命名 BOSS（3 种轮换：玄铁堡垒 / 雷穹母舰 / 赤炎妖机），
  顶部大血条带阶段刻度，登场横幅 + 下滑运镜；血量降到 66% / 33% 时进入新阶段：
  环形弹幕 → 瞄准连发 → 扇形扫射 + 召唤杂兵（受同屏上限约束，不会截成半截编队）；
  击杀时全屏清弹 + 慢动作 + 大额分数 + 必掉火力/炸弹/维修三件道具。
* **武器多样化**：LV3+ 且拾取 **追踪**（12 秒）后追加追踪导弹（锁定最近目标，BOSS 也算合法目标）；
  新道具 **激光**（8 秒贯穿光束，每 0.15s 对每个目标只结算一次，不会一束秒掉 BOSS）；LV5 弹幕加宽 25%。
* **4 种新敌机 + 编队波次**：分裂体（死亡裂成 2 个小体，受同屏上限约束、子体不再分裂）、
  布雷艇（在上方悬停并落下悬浮地雷，地雷属于敌弹，炸弹/超必杀/BOSS 死亡清屏会一并清掉）、
  狙击手（红色矄准线锁定玩家 0.7s 后直射一发，预告线不跟随）、母舰（顶部悬停，持续产杂兵，
  预留名额不会抢 BOSS 召唤）；随机单刷已改为编队波次：V 字 / 一字 / 双侧钳形 / 正弦列车 / 缺口墙，
  规模随难度与时长 ramp，缺口墙永远留一个可穿的空位。
* 拾取掉落道具：火力 / 护盾 / 维修 / 炸弹 / 加分 / 速射 / 激光 / 追踪。
* **打击感打磨**：重型机 / 母舰 / BOSS 被击破时有**顿帧**（0.09 / 0.07 / 0.16 秒，杂兵没有，
  每秒上限 0.35 秒，只缩放敌机与子弹的时间，不拖慢游戏时钟）；**5 / 10 / 20 连杀横幅** + 连杀铃音；
  屏外**狙击手 / 母舰**在顶部边缘有红色危险箭头（最多 6 个，落在玩法区内，不会被系统栏吃掉）；
  BOSS 登场播**警报音**（`AudioFx.ALARM`）。
* 敌人：杂兵、俯冲机、炮艇、精英、重型轰炸机（小 boss）+ 4 种新敌机。
* 随机事件：敌袭、狂暴、EMP、道具雨、缓速场、重型轰炸机登场。
* 无关卡，计分 + 本地排行榜（前 10 名，可改名）。
* 音乐、音效、震动反馈、粒子/冲击波/屏幕震动等特效。

## 屏幕适配（不硬编码像素）

所有尺寸都从**物理屏幕**算出来，不用固定像素：

* `dp(v) = v * density * uiScale`，`uiScale = clamp(min(宽,高)dp / 360, 0.8, 1.5)`，
  360dp 是常见手机参考宽度，小屏整体缩小、大屏整体放大（飞机、敌机、子弹、字体一起缩放）。
* 状态栏/刘海/手势条用 `WindowInsets`（API 30+，含 `displayCutout`），
  旧系统回退到 `status_bar_height` / `navigation_bar_height` 框架 dimen，
  得到 `topPad` / `botPad`，HUD、飞机活动范围、菜单都基于它们。
* 标题页/暂停页的选项区（难度 + 灵敏度 + 开关）是**底部对齐的一整块**，
  高度不够时整块按 `fit` 缩小；排行榜行数按剩余空间自动决定（1~10 行）。
* 启动日志会打印算出的布局，方便在别的机型上核对：
  `layout 1080x2300 density=2.625 uiScale=1.14 fit=1.00 top=149 bot=78`

用 `wm size/density` 可以模拟其它机型（测完 `reset`）：

| 模拟分辨率 | 视图 | uiScale | fit |
|----|----|----|----|
| 720x1280 @320 | 720x1204 | 1.00 | 1.00 |
| 640x1136 @320 | 640x1060 | 0.89 | 1.00 |
| 1440x3200 @560 | 1440x3067 | 1.14 | 1.00 |
| 2280x1080 @420 | 1042x2280 | 1.10 | 1.00 |
| 1080x900 @420 | 868x1080 | 0.92 | 0.99 |

## 难度（3 档，可自选）

标题页和暂停页的 **难度** 选择条，默认 **简单**（即最初的调校）：

| | 敌机生成间隔 | 敌机血量 | 敌机速度/弹速 | 敌机开火 | 事件频率 | 道具掉落 | 同屏敌机上限 | 得分倍率 |
|----|----|----|----|----|----|----|----|----|
| 简单 | x1.00 | x1.00 | x1.00 | x1.00 | x1.00 | x1.00 | 22 | **x1.00** |
| 普通 | x0.70 | x1.35 | x1.15 | x1.35 | x0.85 | x0.85 | 28 | x1.25 |
| 困难 | x0.48 | x1.80 | x1.32 | x1.75 | x0.70 | x0.70 | 34 | x1.60 |

「难度 ramp」也一起放大（`x1 / x1.35 / x1.7`），所以高难度下更早就出现炮艇、精英和重型轰炸机，
编队规模更大（x1 / x1.25 / x1.55）。难度会写进排行榜条目，榜单每行会显示当时的难度。

BOSS 血量不在这张表里：它走 `Boss.java` 的 `HP[] × (1 + 0.35 × 难度) × (1 + 0.25 × 已击杀数)`，
是独立的一套（`{80, 110, 140}` 基础值）。改难度曲线时两处都要看，否则 BOSS 会比杂兵更难/更易得离谱。

## 拖动灵敏度（7 档，可自选）

标题页和暂停页都有 **拖动灵敏度** 选择条：

| 档 | 1 | 2 | 3 | 4 | 5 | **6** | 7 |
|----|----|----|----|----|----|----|----|
| 系数 | 0.30 | 0.45 | 0.65 | 0.85 | 1.10 | **1.40（默认）** | 1.80 |

左边「慢」右边「快」，第 6 档是默认档。
选择会保存在 `SharedPreferences`（`planewar_opts`），重启后仍然有效。
飞机位移 = 手指位移 × 系数，速度上限 = `760dp/s × (0.5 + 0.95 × 系数)`（默认档约 1390dp/s）。

## 图标层（不再用汉字当图标）

早期所有元素都是「一个边框 + 一个中文汉字」的临时方案（盾/命/爆/分/速/激光/追/火、超/蓄、炸、开/关、慢/快）。
现在换成一套 Canvas 矢量图标：

* `Icons.java`：17 个图标（盾、十字、炸弹、星、闪电、激光、导弹、火焰、星芒、音符、喇叭、震动、
  左/右尖角、飞机、开关），全部用**调用方传入的** `Paint`/`Path`/`RectF` 画，每帧零分配，
  画完恢复画笔状态；不依赖位图资源，也不依赖中文字体。
* `UiSpec.java`（**纯 Java**）：道具的**颜色 / 短标签 / 图标**唯一真值表，`color(kind)`、`label(kind)`、
  `iconOf(kind)`，外加最小可读尺寸 `ICON_MIN_DP`、药丸图标 `PILL_ICON_DP`、图标相对边框比例
  `POWER_ICON_RATIO` / `SKILL_ICON_RATIO`。`GameView` 的 `powerColor`/`powerLabel` 只是它的别名。
* 依赖方向单向：`Icons` → `UiSpec`（`UiSpec` 不许 import Android，否则 JVM 单测守卫会 FAIL）。
* 落点：掉落道具盒（图标在旋转框内，短标签移到盒子下方）、两枚主动技能按钮、HUD 状态药丸、
  设置开关（配一个开关图形表示开/关）、灵敏度两侧的尖角、标题页的道具图例。

## 战机美术层（13 架飞机全部重画）

以前 13 架飞机都是「一个多边形 + 一个实心圆点」，15dp 的小敌机和 60dp 的 BOSS 长得一样。
现在换成一套 Canvas 矢量美术层，全部代码画，**不新增任何位图/字体/权限资源**：

* `Ships.java`：13 套剪影（玩家拦截机、9 种敌机、3 种 BOSS），入口只有一个
  `Ships.draw(c, p, path, rect, id, cx, cy, r, t, tint, flame, rot, aux)`。约定：
  复用调用方的 `Paint`/`Path`/`RectF`，**每帧零分配**（所有 `Path`/`Shader`/颜色数组都是
  `static final`），画完恢复画笔状态；所有路径在**单位空间** `[-1,1]`（机头朝上），
  渐变也在单位空间里算，所以一份静态 `Shader` 在任意尺寸下都对；敌机由调用方转 180°。
* `ShipSpec.java`（**纯 Java**）：美术唯一真值表 — 每架飞机的识别色/上壳色/下壳色/点缀色/
  座舱色/修边色/光晕色、剪影编号 `SIL_*`、机头半径 `noseR`、核心灯半径 `coreRadiusDp`、
  每架飞机的图层配额 `layers`（grunt/darter/sniper 4，splitter/gunship/elite/miner 6，
  heavy/mother 7，玩家 8，BOSS 9），以及颜色算子 `mix`/`shade`/`tint`/`alpha`。
  `Ships` 不许自己发明颜色，只从 `ShipSpec` 取。
* **识别色不变**：`ShipSpec.identity(id)` 逐字节等于旧版 `GameView` 里的 `E_*` 常量，
  `check.py` 的 9 个采样点仍然命中；每架飞机都在正中心用纯识别色画一颗不透明核心灯
  （半径下限 `CORE_MIN_DP = 2.5dp`），保证小尺寸下也能一眼认出是什么机。
* 保留原有表现：受击白闪 `e.flash`、玩家无敌闪烁、倾斜 `bank`、护盾泡泡、狂热/连击火焰颜色、
  狙击锁红线、BOSS 三阶段核心灯（读 `Boss.PHASES`）、血条、HUD 全部不变。
* 测试钩子：`am start ... --ei show N` 把前 N 种敌机排成两行悬停（不射击、不掉血、不爆炸），
  日志 `show n=`，专供截图逐架比对美术。

## 构建

```sh
cd ~/fix/plane
./build.sh          # -> plane.apk
./install.sh        # root 安装并启动
```

工具链：`aapt2` + `javac`（`--release 8` + android.jar 33）+ `r8.jar` + `apksigner`。
`build.sh` 会检查 `classes.dex` 里是否包含 `MainActivity`（R8 的 `-dontobfuscify` 选项不存在，
真正起作用的是 `-dontobfuscate`，见 `proguard-rules.pro`）。

## 自动化测试

```sh
./test_jvm.sh       # JVM 单测：纯逻辑类（Tuning/Super），无需手机，~2s
./build.sh          # -> plane.apk
DIFF=1 ./test.sh    # 真机自动验收（0 简单 / 1 普通 / 2 困难）
```

`test_jvm.sh` 只编译不 import Android 的纯逻辑类（`Tuning.java`、`Super.java`、`Boss.java`、
`Weapon.java`、`Wave.java`）+ `test/TestLogic.java` / `test/TestBoss.java`；
脚本会先检查这 5 个类没有 `import android`（有就 FAIL），再跑两套断言
（当前 7921 + 54654 项）：难度表单调与同屏上限 22/28/34、擦弹几何、狂热阈值、超必杀主题表、
BOSS 阶段阈值/不可逆/召唤上限/活性、编队坐标在 320/700/1400 三种宽度下不越界、弹形表与锁定选择、
连杀里程碑与顿帧预算。
新玩法的数值一律先进这些类，保证能在 JVM 上断言。

用 intent 参数驱动游戏，不需要点击屏幕：

| 参数 | 作用 |
|------|------|
| `--ez autostart true` | 直接进入游戏 |
| `--ez autopilot true` | 自动闪避、自动瞄准，**并且会放超必杀/炸弹**（与手动同一个入口） |
| `--ez god true` | 不会掉血 |
| `--ei report N` | 每 N 秒打印一次 `stats` |
| `--ei autodie S` | 飞行 S 秒后坠毁（触发结算/排行榜） |
| `--ei diff N` | 0 简单 / 1 普通 / 2 困难，例如 `DIFF=2 ./test.sh` |
| `--ei super N` | 本局内自动释放 N 次超必杀（日志 `super name=…`） |
| `--ei bomb N` | 本局内自动引爆 N 颗炸弹（日志 `bomb n=…`） |
| `--ei superall N` | 依次强制释放前 N 种超必杀主题（去随机化，保证 5 种主题都被跑到） |
| `--ei graze N` | 本局内自动擦弹 N 次（走与碰撞同一个 `grazeHit()`，但与真实擦弹分开计数：日志 `grazeHook n=…`，真实擦弹才写 `graze n=`） |
| `--ei boss N` | 本局内强制出场 N 只 BOSS（t=6s 起，不等 t=80s；日志 `boss spawn name= hp=` / `boss killed`） |
| `--es powerup laser` / `--es powerup tracking` | t=6s 起在机头上方掎落一排道具（走真实 `collect()`，无测试旁路）；日志 `pup hook drop kind=` / `pup kind=laser t=8.0` / `pup kind=homing t=12.0` |
| `--ei powerall N` | t=6s 起在屏幕中部悬停生成 N 种道具各一个（一排），一张截图就能看全整套图标；日志 `pup hook row n=` |
| `--ei wave N` | t=1s 起 N 个编队波次 + 4 种新敌机各一只；日志 `wave kind= n= type=` / `enemy spawn type=` / `split n=` / `mine n=` / `sniper aim` / `mother spawn n=` / `clear mines=` |

测试会：卸载重装 → 自动飞行 26 秒 → 检查 `gameover score=`、事件、**5 种超必杀主题全部释放且无异常**
（`superFails=0`）、炸弹日志、**自然擦弹**（`graze=` 来自真实碰撞，钩子另记 `grazeHook=`）、`fever=1`、
**BOSS 出场且被击破**（`boss spawn name=` / `boss killed`）、激光与追踪导弹真的生效（`laser tick` / `missile n=`）、
**4 种新敌机与编队全部出现**（`wave kind=` / `split n=` / `mine n=` / `sniper aim` / `mother spawn`）、
**打磨项**（`streak=` / `hitstop=` / `danger=`）、**帧率**（`fps=` 必须 >= 45）、
**音效数**（`audio ready: N sfx`，`N >= SFX_MIN=19`）→ 重启并检查排行榜从磁盘读回（`loaded best=`）。
`AudioFx.VER` 与 `SFX_MIN` 必须一起 bump：新增音效而不改 VER，设备会继续用旧 wav 缓存，数量就对不上。
三档难度实测（同一 APK 连续跑，均为 PASS）：

| 难度 | score | 自然擦弹 | BOSS | 帧率 | 音效 |
|----|----|----|----|----|----|
| 简单 DIFF=0 | 68,567 | 12 | spawn=1 killed=1 | 60.2 | 19 |
| 普通 DIFF=1 | 105,708 | 30 | spawn=1 killed=1 | 60.2 | 19 |
| 困难 DIFF=2 | 209,889 | 26 | spawn=1 killed=1 | 60.2 | 19 |

`stats` 行形如
`stats t=12 score=2427 kills=11 weapon=1 lives=3 enemies=1 energy=37 bomb=0 graze=0 fever=0 boss=0 fps=60.2`。
日志：`su -c "logcat -d -s planewar:V"`。

注意：游戏循环由 vsync 驱动，屏幕熄灭后不刷新；这台手机 30s 就息屏，而 DeX 窗口下
`KEYCODE_WAKEUP` 不总能把画面拉回来，所以 `test.sh` 会把 `stay_on_while_plugged_in` 临时设为 7，
退出时（含失败）自动还原原值。

## 截图检查

```sh
./shot.sh --ez autostart true --ez god true --ei powerall 8   # 用钩子重启游戏并截图
./shot.sh                                                   # 只截当前画面
python pngcrop.py 0 950 1440 1300 "$TMPDIR/crop.png" 1       # 裁一块像素区域存成 png
su -c "screencap -p /data/local/tmp/screen.png && chmod 644 /data/local/tmp/screen.png"
cp /data/local/tmp/screen.png "$TMPDIR/screen.png"
python view.py "$TMPDIR/screen.png" 78 70 84   # cols, y0%, y1%
python check.py "$TMPDIR/screen.png"           # 按颜色统计飞机/敌人/道具
python crop.py 259 1576 820 1660 60            # 放大某块像素区域（x0 y0 x1 y1 cols）
```

`shot.sh` 会先唤醒屏幕、回桌面（Android 14+ 从 shell 起 Activity 需要桌面在最前），再带钩子启动，
等 `WAIT` 秒（默认 9）后截图，最后跑 `view.py`；`PIX=1` 追加 `check.py`。

注意：`input tap` 用的是屏幕坐标，游戏视图从 y=100 开始（导航栏），
所以点视图里的 (x, y) 要 `input tap x (y+100)`。日志 `size 1080.0x2300.0` 可确认。

## 文件

| 文件 | 说明 |
|------|------|
| `PROGRESS.md` | 本次 Duo 会话的进度与环境事实速查（交接用） || `src/dev/termux/planewar/GameView.java` | 游戏主循环、绘制、触摸、敌人、事件、粒子、主动技能 |
| `src/dev/termux/planewar/MainActivity.java` | 全屏 Activity，读取测试 intent 参数 |
| `src/dev/termux/planewar/Tuning.java` | 新玩法数值总表（能量/炸弹/擦弹/狂热），按 3 难度缩放，**纯 Java** |
| `src/dev/termux/planewar/Super.java` | 5 种超必杀主题的伤害/范围/效果，**纯 Java** |
| `src/dev/termux/planewar/Boss.java` | 3 阶段 BOSS 状态机（阶段阈值/弹幕节奏/召唤上限），**纯 Java**；**BOSS 血量走这里的 `HP[]`×(1+0.35×难度)，与 `Tuning` 的敌机难度系数是两套** |
| `src/dev/termux/planewar/Weapon.java` | 玩家弹形表（LV1-5）、导弹数、激光结算周期、锁定目标选择，**纯 Java** |
| `src/dev/termux/planewar/Wave.java` | 编队图案（V/一字/钳形/正弦/缺口墙）→ (delay, type, lane)，lane 为 [0,1] 分数，**纯 Java** |
| `src/dev/termux/planewar/AudioFx.java` | 运行时合成 15 种音效 + 循环芯片音乐 |
| `src/dev/termux/planewar/Vibro.java` | 震动封装（API 24 / 31+） |
| `src/dev/termux/planewar/Scores.java` | 本地排行榜（SharedPreferences） |
| `src/dev/termux/planewar/Entities.java` | 子弹/敌人/道具/粒子数据类 |
| `test/TestLogic.java` + `test_jvm.sh` | 纯逻辑类的 JVM 断言（无需手机） |
| `src/dev/termux/planewar/Icons.java` | 17 个 Canvas 矢量图标，零分配绘制（复用调用方 Paint/Path/RectF） |
| `src/dev/termux/planewar/Ships.java` | 13 架飞机的 Canvas 矢量美术层（单位空间路径 + 静态渐变，每帧零分配） |
| `src/dev/termux/planewar/ShipSpec.java` | 飞机美术唯一真值表（识别色/剪影/图层配额/核心灯半径），**纯 Java** |
| `test/TestShips.java` | `ShipSpec` 的 JVM 断言（识别色逐字节不变、图层配额、帧操作上限、核心灯下限） |
| `src/dev/termux/planewar/UiSpec.java` | 道具颜色/短标签/图标唯一真值表 + 图标尺寸常量，**纯 Java** |
| `test/TestUi.java` | `UiSpec` 的 JVM 断言（真值表完整性、颜色逐字节不变、图标映射全覆盖） |
| `shot.sh` / `pngcrop.py` | 带钩子启动并截图 / 把截图的一块区域裁成 png 看细节 |
| `gen_icon.py` | 生成各密度启动图标 |
| `view.py` / `check.py` / `crop.py` | 截图 ASCII 预览 / 颜色校验 / 区域放大 |
