# 发布日志：plane → 独立公开仓库

目标：把 `plane/` 单独初始化一个 git 仓库，用 `gh` 在 atfa 账号下建 public 仓库，发布 Release（APK）与源码。

## 结论（发布完成后填地址）

* 公开仓库：https://github.com/atfa/plane-war
* Release：v1.0.0，资产 `plane.apk`（md5 `ed0635b6a30a1a43984cfb6ec08fa717`，与手机上实测通过的那份逐字节一致）
* 历史：保留 `plane/` 在父仓库里的真实开发提交（`git subtree split` + `git filter-branch` 剥离构建产物），不是全新历史

## 双仓库约定（重要）

`plane/` 现在同时被两个仓库管理：父仓库 `development-on-Android`（私有，跟踪 `plane/**` 文件）与 `plane/.git`（公开仓库 `atfa/plane-war`）。它们**共用同一份工作区文件**，所以：

* 改了 plane 的代码要在两边各提交一次：`git -C plane add -A && git -C plane commit && git -C plane push`，然后回父仓库 `git add plane && git commit`。
* **不要在父仓库 `git rm -r plane`** —— 那会连公开仓库的目录（含 `plane/.git`）一起删掉。
* 父仓库不受影响：git 扫目录时跳过 `.git`，实测父仓库 `git status --porcelain` 完全干净；`git add -A` 也不会把 `plane/` 变成 gitlink（gitlink 只在目录未被父仓库跟踪时发生）。
* 构建产物（`out/`、`plane.apk`、`*.idsig`）在公开仓库里被 `.gitignore` 排除，父仓库继续按原约定跟踪它们。

## 过渡态

嵌套仓库建好、但 Duo 还没把本轮改动交付到父仓库 `main` 之前，`cd plane && git status` 会显示一批 modified/deleted（它的 HEAD 是新内容，工作区还是旧内容）。父仓库 `main` 落地后文件内容就等于嵌套仓库的 HEAD，**自动变干净**，不需要手动处理。

## 步骤日志

* 步骤 1：改造 plane/ 为可发布内容 —— README 重写（中英 + 「全程手机开发」段落 + skill/pi/duo 三链接 + Releases 下载 + 构建/测试说明）、新增 `LICENSE`(MIT) 与 `.gitignore`、`build.sh` 的 android.jar 缺失提示改指 skill（原来指向父仓库里的 `../apkdemo/get-sdk.sh`）、`install.sh` 在 APK 缺失时提示先跑 `build.sh`。
* 步骤 1b（Tony 实测出的真缺陷）：`view.py` / `check.py` / `crop.py` / `pngcrop.py` 四个开发工具把 `ascii.py` 的路径写死成 `~/fix/.agents/skills/android-on-device-apk/scripts`，而**这个路径在本机根本不存在**（`~/.agents/...` 也不存在），前两轮能跑全靠手动 `export PYTHONPATH=<worktree>/.agents/skills/...` —— 第三方 clone 下来直接 ImportError。修法：把 `ascii.py` 内置到 `tools/`（纯 stdlib，99 行，文件头注明出处），查找顺序改为 `$SKILL_SCRIPTS` → 仓库 `tools/` → 开发机路径（`tools/skillpath.py`）。现在不带任何 PYTHONPATH 跑 `check.py` 九桶全非零。
* 步骤 2：真机截图进 `docs/screenshots/`。三张都是这台手机 `shot.sh` 拍的（屏幕 1440x2304），裁到游戏窗口 776x1424（原图 x 332..1108, y 366..1790）：`showcase.png`（`--ei show 9` 美术展示钩子，9 种敌机按体型分三行同屏 + 玩家机，392 KB）、`battle.png`（战斗中：得分/连击/护盾/火力/技能充能/时间/击坠，309 KB）、`title.png`（标题界面：难度/拖动灵敏度/排行榜 TOP3，164 KB）。README 顶部新增「截图」章节（表格三列 + 拍摄命令）。
* 步骤 3：`git subtree split -P plane` + `git filter-branch` 剥离 `out/`、`plane.apk`、`*.idsig`（实测 32 条提交、pack 190 KiB），在用户仓库 `plane/` 里 `git init` 并导入该 tip，加 remote `origin=https://github.com/atfa/plane-war.git`，随后父仓库 `git checkout -- plane` 恢复干净。
* 步骤 4：`gh repo create atfa/plane-war --public --push`。
* 步骤 5：`gh release create v1.0.0 plane.apk`。
* 步骤 6：父仓库 README 指针行 + 本文件汇总。
* 步骤 7：端到端验收（全新 clone → build → JVM 四套断言 → 装机 → 三档自动试玩 → 像素校验 → Release 资产 md5 比对）。
