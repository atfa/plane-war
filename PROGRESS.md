# Duo 会话进度 · plane「更精彩」升级

会话 `20261116-031515-41b3a780`（STEP 模式，8 步队列）。任务：**分析飞机战斗游戏并让它更精彩**。
目标代码 = `plane/`（包 `dev.termux.planewar`）。分工：Austin 实现，Tony 独立验证（`[fast]` 步骤由 Tony 用
`duo_set_verification` 验收，`[goal]` 步骤双签）。

## 队列状态

| # | 步骤 | 状态 |
|---|---|---|
| 1 | 能量超必杀 + 炸弹库存 | ✅ 已提交 `531ba82`，已提交验证（REVIEW） |
| 2 | 擦弹 graze + 连击狂热 | ✅ 已提交，已提交验证 |
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

## 步骤 2 已落地内容

- **擦弹**：`Tuning.grazes(dx,dy,hitR,bandPx)` 纯几何（环形：内边包含、外边不包含，与直击判定
  `d2 < rr*rr` 严格互补）；`collisions()` 在敌弹直击判定**之后**才查擦弹带（直击绝不发奖励），
  用 `Bullet.grazeT` + `Tuning.GRAZE_COOLDOWN=0.25` 防同一颗弹刷能量；`grazeHit()` 是
  碰撞与钩子共用的唯一入口（充能 + 连击 + 分数 + 浮动“擦弹” + `AudioFx.GRAZE`）。
- **狂热**：`feverActive()` 步骤 1 已接好（x2 得分 / 0.7 射速 / 尾焰 / HUD pill），步骤 2 只补了
  上升沿横幅 `checkFever()`（`killEnemy()` 与 `grazeHit()` 都调），graze 嗂连击使 fever 可达。
- 新钩子 `--ei graze N`（t=6s 起每 0.35s 一次）；日志 `graze n=`（第 1、10 次与之后每 25 次）；
  `gameover` 行末尾追加 ` graze=`。
- `AudioFx` 新增 `GRAZE`，`VER` 提 v5（缓存 wav 会重建），音效 14→15 种。
- JVM 断言 70 → **148 项**（新增 `grazeAndFever()`：环形几何/方向无关/带越宽越宽/冷却防刷能/fever 阈值）。
- 实测：`DIFF=1 ./test.sh` PASS（score=21229 super=6 bomb=1 graze 12 fever=1 出现 2 行 fps=60.2
  `loaded best=21229`，audio 15 sfx）。

## 步骤 3 起手式（多阶段 BOSS，`[goal]` 双签）

- 必须加 `--ei boss N` 钩子：`autodie 26` 永远到不了 t=80s。
- BOSS 血条高度 <= dp(14)，放在 `topPad` 下方，登场用现有 `banner()`，不堆 HUD。
- 同屏敌机上限：简单 22 / 普通 28 / 困难 34（BOSS 召唤杂兵要走这个 cap）。
- 建议拆 `Boss.java`（纯 Java 3 阶段状态机，输出开火/召唤请求），配套 JVM 断言。
