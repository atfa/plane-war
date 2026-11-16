# Duo 会话进度 · plane「更精彩」升级

会话 `20261116-031515-41b3a780`（STEP 模式，8 步队列）。任务：**分析飞机战斗游戏并让它更精彩**。
目标代码 = `plane/`（包 `dev.termux.planewar`）。分工：Austin 实现，Tony 独立验证（`[fast]` 步骤由 Tony 用
`duo_set_verification` 验收，`[goal]` 步骤双签）。

## 队列状态

| # | 步骤 | 状态 |
|---|---|---|
| 1 | 能量超必杀 + 炸弹库存 | ✅ 已提交 `531ba82`，已提交验证（REVIEW） |
| 2 | 擦弹 graze + 连击狂热 | 待做 |
| 3 | 多阶段 BOSS 战（`[goal]`） | 待做 |
| 4 | 武器多样化（追踪导弹 / 激光） | 待做 |
| 5 | 4 种新敌机 + 编队调度器 | 待做 |
| 6 | 表现打磨（顿帧 / 连杀横幅 / 危险提示） | 待做 |
| 7 | test.sh 全难度回归 + README + 产物 | 待做 |
| 8 | 纯逻辑拆文件 + JVM 单测（Boss/Weapon/Wave 尚未建） | 待做 |

## 步骤 1 已落地内容（`531ba82`）

- 主动技能：能量超必杀（右下「超」，5 主题：烈焰流星/万雷齐鸣/圣光普照/大地裂变/冰封万里）+
  炸弹库存（左下「炸」，开局 1 上限 3，「爆」道具改为 +1 库存，满了折算 400 分）。
- 新纯 Java 逻辑类：`Tuning.java`（新数值总表，按 3 难度缩放）、`Super.java`（5 主题参数），均不 import Android。
- `plane/test_jvm.sh` + `plane/test/TestLogic.java`：54 项 JVM 断言（无需手机，~2s）。
- `stats` 周期日志扩为 `... energy= bomb= graze= fever= boss= fps=`（fps = 滚动平均帧率）。
- `test.sh`：传 `--ei super 1 --ei bomb 1`，断言 `super name=` / `bomb n=` / stats 字段齐全 / `fps>=45`。
- 顺带修的两个真 bug：`Scores` 落盘 `apply()`→`commit()`（进程被杀丢排行榜）；
  `test.sh` 期间 `stay_on_while_plugged_in=7`（本机 30s 息屏会让 vsync 游戏循环停摆，trap EXIT 还原）。
- autopilot 通过 `autoSkill()` 调用与手动**同一个** `fireSuper()` / `useBomb()`（无测试专用旁路）。
- Tony 验出并修掉的闪退：`boltX/boltT` 按 `Super.COUNT`（5）开，而万雷齐鸣 `HITS=6` →
  每次超必杀 20% 概率 `ArrayIndexOutOfBoundsException` 直接杀进程。现按 `Super.MAX_IMPACTS`
  （= max(HITS)，静态初始化算出）开数组，三处遍历上界统一用 `boltT.length`；
  JVM 加不变量 `impacts(t) <= MAX_IMPACTS`（54→70 项）；验收去随机化：新钩子
  `--ei superall N` 依次强制释放前 N 种主题，`test.sh` 断言 5 个主题名全出现且 `superFails=0`
  （`fireSuper` 包 try/catch 计数，gameover 行末尾追加 `superFails=`，原有字段顺序未动）。

实测（步骤 1 验收）：`./test_jvm.sh` 70 项全绿；`DIFF=1 ./test.sh` PASS
（score=10016 kills=26 super=6 含全部 5 主题 bomb=1 fps=60.2 superFails=0 `loaded best=10016`，
`logcat -b crash` 为空）。

## 关键环境事实（照抄，省得重测）

- 真机：`su` = root(ksu)；`aapt2`/`javac`/`apksigner`/`keytool`、`~/android-sdk/platforms/android-33`、
  `~/android-tools/r8.jar` 就位。`./build.sh` 约 14s。
- **设备是共享的**：跑 `test.sh` 前与 Tony 用 `duo_send` 约定设备占用。
- 窗口尺寸会变：DeX 横屏 `977x865 uiScale=1.37 fit=0.76 top=120 bot=57`；竖屏 `779x1427 uiScale=1.24 fit=1.00`。
  HUD 新元素必须两种尺寸都不打架（技能按钮把文字全画在圆内，底部两角）。
- 26s 自动试玩里同屏敌机常为 0–1 架，`autodie 26` 永远看不到 t=80s 的东西 → 新特性一律配强制钩子。
- 验收命令：`cd plane && ./test_jvm.sh && ./build.sh && DIFF=1 ./test.sh`（步骤 7 额外跑 DIFF=0/2）。
- `plane/plane.apk`、`plane/out/` 受版本控制 → 改源码的步骤连产物一起提交（`plane/out/test/` 已 gitignore）。

## Tony 的验收口径（会据此打回）

1. 功能日志必须真机出现：`super name=`/`bomb n=`（1）、`graze n=`（2）、`boss spawn name=`/`boss killed`（3）。
2. autopilot 必须与手动同一入口；`stats` 字段 + `fps>=45` 必须断言。
3. 步骤 8 的拆文件必须是**行为等价**重构，不许顺手改数值。
4. `Scores.java` 条目格式不动（存档只加键），`loaded best=` 断言必须继续通过。

## 下一步（步骤 2 起手式）

擦弹（graze）：在 `collisions()` 里对每颗敌弹判断与飞机的距离落在 `Tuning.GRAZE_RADIUS_DP` 环带内，
用 `Bullet.grazeT` 做冷却（字段已加），命中 → `energy += Tuning.GRAZE_ENERGY`、`graze++`、浮动「擦弹」；
连击 ≥ `Tuning.FEVER_COMBO` 进入狂热（`feverActive()` 已实现，已接进 `comboMult()` x2 与 `fire()` 射速、
机身尾焰与 HUD pill）。新增 `AudioFx.GRAZE` 音效（记得把 `VER` 提到 v5 以重建缓存 wav）、
钩子日志 `graze n=`、`test.sh` 断言。
