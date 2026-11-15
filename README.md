# 雷霆战机 (Plane War)

2D 竖版飞机大战，完全在这台 Android 手机里（Termux）编译、签名、安装，不需要 PC / Android Studio / Gradle。

## 玩法

* **机头始终朝上**，屏幕任意位置**拖动**即可让飞机上下左右移动（不用点到飞机）。
* 松手后飞机滑行一小段距离停下；飞机碰到屏幕边缘会停下并贴边。
* 飞机自动开火，无需按键。
* 拾取掉落道具：火力 / 护盾 / 维修 / 炸弹 / 加分 / 速射。
* 敌人：杂兵、俯冲机、炮艇、精英、重型轰炸机（小 boss）。
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

## 拖动灵敏度（7 档，可自选）

标题页和暂停页都有 **拖动灵敏度** 选择条：

| 档 | 1 | 2 | 3 | 4 | 5 | **6** | 7 |
|----|----|----|----|----|----|----|----|
| 系数 | 0.30 | 0.45 | 0.65 | 0.85 | 1.10 | **1.40（默认）** | 1.80 |

左边「慢」右边「快」，第 6 档是默认档。
选择会保存在 `SharedPreferences`（`planewar_opts`），重启后仍然有效。
飞机位移 = 手指位移 × 系数，速度上限 = `760dp/s × (0.5 + 0.95 × 系数)`（默认档约 1390dp/s）。

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
./test.sh
```

用 intent 参数驱动游戏，不需要点击屏幕：

| 参数 | 作用 |
|------|------|
| `--ez autostart true` | 直接进入游戏 |
| `--ez autopilot true` | 自动闪避、自动瞄准 |
| `--ez god true` | 不会掉血 |
| `--ei report N` | 每 N 秒打印一次 `stats` |
| `--ei autodie S` | 飞行 S 秒后坠毁（触发结算/排行榜） |
| `--ei diff N` | 0 简单 / 1 普通 / 2 困难，例如 `DIFF=2 ./test.sh` |

测试会：卸载重装 → 自动飞行 26 秒 → 检查 `gameover score=`、事件、音效 → 重启并检查
排行榜从磁盘读回（`loaded best=`）。日志：`su -c "logcat -d -s planewar:V"`。

注意：游戏循环由 vsync 驱动，屏幕熄灭后不刷新，所以测试脚本会周期性发送 `KEYCODE_WAKEUP`。

## 截图检查

```sh
su -c "screencap -p /data/local/tmp/screen.png && chmod 644 /data/local/tmp/screen.png"
cp /data/local/tmp/screen.png "$TMPDIR/screen.png"
python view.py "$TMPDIR/screen.png" 78 70 84   # cols, y0%, y1%
python check.py "$TMPDIR/screen.png"           # 按颜色统计飞机/敌人/道具
python crop.py 259 1576 820 1660 60            # 放大某块像素区域（x0 y0 x1 y1 cols）
```

注意：`input tap` 用的是屏幕坐标，游戏视图从 y=100 开始（导航栏），
所以点视图里的 (x, y) 要 `input tap x (y+100)`。日志 `size 1080.0x2300.0` 可确认。

## 文件

| 文件 | 说明 |
|------|------|
| `src/dev/termux/planewar/GameView.java` | 游戏主循环、绘制、触摸、敌人、事件、粒子 |
| `src/dev/termux/planewar/MainActivity.java` | 全屏 Activity，读取测试 intent 参数 |
| `src/dev/termux/planewar/AudioFx.java` | 运行时合成 13 种音效 + 循环芯片音乐 |
| `src/dev/termux/planewar/Vibro.java` | 震动封装（API 24 / 31+） |
| `src/dev/termux/planewar/Scores.java` | 本地排行榜（SharedPreferences） |
| `src/dev/termux/planewar/Entities.java` | 子弹/敌人/道具/粒子数据类 |
| `gen_icon.py` | 生成各密度启动图标 |
| `view.py` / `check.py` / `crop.py` | 截图 ASCII 预览 / 颜色校验 / 区域放大 |
