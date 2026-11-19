# 发布日志：plane → 独立公开仓库

目标：把 `plane/` 单独初始化一个 git 仓库，用 `gh` 在 atfa 账号下建 public 仓库，发布 Release（APK）与源码。

## 结论（发布完成后填地址）

* 公开仓库：https://github.com/atfa/plane-war
* Release：https://github.com/atfa/plane-war/releases/tag/v1.0.0 （`雷霆战机 v1.0`，非 draft 非 prerelease），资产 `plane.apk` 90,921 B（md5 `ed0635b6a30a1a43984cfb6ec08fa717`，与手机上实测通过的那份逐字节一致）
* 历史：保留 `plane/` 在父仓库里的真实开发提交（`git subtree split` + `git filter-branch` 剥离构建产物），不是全新历史

## 发布结果汇总

| 项 | 值 |
|----|-----|
| 仓库 | https://github.com/atfa/plane-war （PUBLIC，默认分支 main，licenseInfo 自动识别为 MIT） |
| Release | https://github.com/atfa/plane-war/releases/tag/v1.0.0（资产 `plane.apk` 90,921 B，md5 `ed0635b6a30a1a43984cfb6ec08fa717`） |
| 包 | `dev.xduo.plane` / versionCode 1 / versionName 1.0 / minSdk 24 / targetSdk 33 |
| 历史 | 35 个真实开发提交（作者 atfa 27 / Tony 5 / Austin 3），零构建产物 |
| 内容 | 46 个文件：源码 + 脚本 + README + 3 张真机截图 + LICENSE + `.mailmap` |
| clone 体积 | `plane/.git` 1.2 MB（截图 856 KB + 源码历史 334 KB） |
| 上游 | skill [dev-apk-on-android](https://github.com/atfa/dev-apk-on-android) · agent [pi](https://github.com/earendil-works/pi) · [duo](https://github.com/atfa/duo) |

`.mailmap` 把开发时两个 agent 提交身份的大小写不一致（`tony` / `Tony`）统一显示为 Tony。APK 只作为 Release 资产发布，不进 git；git 里只有源码与文档，clone 下来就能 `./build.sh` 重现出同一个包。

## 双仓库约定（重要）

`plane/` 现在同时被两个仓库管理：父仓库 `development-on-Android`（私有，跟踪 `plane/**` 文件）与 `plane/.git`（公开仓库 `atfa/plane-war`）。它们**共用同一份工作区文件**，所以：

* 改了 plane 的代码要在两边各提交一次：`git -C plane add -A && git -C plane commit && git -C plane push`，然后回父仓库 `git add plane && git commit`。
* **不要在父仓库 `git rm -r plane`** —— 那会连公开仓库的目录（含 `plane/.git`）一起删掉。
* 父仓库不受影响：git 扫目录时跳过 `.git`，实测父仓库 `git status --porcelain` 完全干净；`git add -A` 也不会把 `plane/` 变成 gitlink（gitlink 只在目录未被父仓库跟踪时发生）。
* 构建产物（`out/`、`plane.apk`、`*.idsig`）在公开仓库里被 `.gitignore` 排除，父仓库继续按原约定跟踪它们。
* 已同步：公开仓库 main 上有一个 `docs: sync publishing notes from the parent repo` 提交，把父仓库步骤 1~6 的 `README.md`（顶部发布横幅）、`PUBLISH.md`（本文）、`.mailmap` 同步过去，commit message 里记着父仓库 main 上对应的提交 SHA；同步后父仓库 `plane/` 工作树内容与公开仓库 HEAD 逐字节一致（`.mailmap` 两边各提交一份，否则父仓库会多出一个 `?? plane/.mailmap`）。

## 过渡态

嵌套仓库建好之后，父仓库 `development-on-Android` 与 `plane/.git` 共用同一份工作区文件。本轮 Duo 已经把步骤 1+2 落地到父仓库 `main`，两边内容一致，所以**两边 `git status` 都是干净的**（实测）。

如果父仓库 `main` 落后于嵌套仓库的 HEAD（例如在 Duo 交付之前，或以后只往公开仓库提交忘了父仓库），`cd plane && git status` 会显示一批 modified/deleted —— 这是预期中的**过渡态**：嵌套仓库的 HEAD 是新内容，工作区还是旧内容。父仓库 `main` 落地后文件内容就等于嵌套仓库的 HEAD，**自动变干净**，不需要手动处理，也**不要**在嵌套仓库里 `git checkout .`（那会把新内容覆盖成旧的）。

## 步骤日志

* 步骤 1：改造 plane/ 为可发布内容 —— README 重写（中英 + 「全程手机开发」段落 + skill/pi/duo 三链接 + Releases 下载 + 构建/测试说明）、新增 `LICENSE`(MIT) 与 `.gitignore`、`build.sh` 的 android.jar 缺失提示改指 skill（原来指向父仓库里的 `../apkdemo/get-sdk.sh`）、`install.sh` 在 APK 缺失时提示先跑 `build.sh`。
* 步骤 1b（Tony 实测出的真缺陷）：`view.py` / `check.py` / `crop.py` / `pngcrop.py` 四个开发工具把 `ascii.py` 的路径写死成 `~/fix/.agents/skills/android-on-device-apk/scripts`，而**这个路径在本机根本不存在**（`~/.agents/...` 也不存在），前两轮能跑全靠手动 `export PYTHONPATH=<worktree>/.agents/skills/...` —— 第三方 clone 下来直接 ImportError。修法：把 `ascii.py` 内置到 `tools/`（纯 stdlib，99 行，文件头注明出处），查找顺序改为 `$SKILL_SCRIPTS` → 仓库 `tools/` → 开发机路径（`tools/skillpath.py`）。现在不带任何 PYTHONPATH 跑 `check.py` 九桶全非零。
* 步骤 2：真机截图进 `docs/screenshots/`。三张都是这台手机 `shot.sh` 拍的（屏幕 1440x2304），裁到游戏窗口 776x1424（原图 x 332..1108, y 366..1790）：`showcase.png`（`--ei show 9` 美术展示钩子，9 种敌机按体型分三行同屏 + 玩家机，392 KB）、`battle.png`（战斗中：得分/连击/护盾/火力/技能充能/时间/击坠，309 KB）、`title.png`（标题界面：难度/拖动灵敏度/排行榜 TOP3，164 KB）。README 顶部新增「截图」章节（表格三列 + 拍摄命令）。
* 步骤 3：把 `plane/` 拆成独立 git 历史，导入用户仓库的 `plane/`。
  * **在 scratch clone 里做 split，不在 worktree 里做**（计划原文写的是 worktree）：git worktree 与用户仓库共用同一个 `.git`，`subtree split` 的分支、`filter-branch` 的 `refs/original`、`git gc --prune=now` 都会落在用户仓库上，而计划里的「删 `refs/remotes/origin/*`」在 worktree 里会删掉用户仓库真正的 `origin/development-on-Android` 远端跟踪 ref。改用 `$HOME/.tmp/split-plane`（`git clone --no-local`），结果等价，用户仓库零污染。
  * `git subtree split -P plane` → 37 条提交（4.2s）；`git filter-branch --index-filter 'git rm -r --cached --ignore-unmatch out plane.apk plane.apk.idsig' --prune-empty` → **35 条**（两个提交只动构建产物，被剪掉），全历史 `git rev-list --objects --all` 里 `out/` / `plane.apk` / `*.idsig` 零命中；作者分布 atfa 27 / Austin 3 / Tony 5（保留真实作者，未做 map-author）。
  * 清掉 `refs/original/*` 与 scratch clone 的 `refs/remotes/origin/*`，`git reflog expire --expire=now --all` + `git gc --prune=now`；然后 `git init -b main` + `git fetch <scratch> split-plane` + `git reset --hard FETCH_HEAD` + `git remote add origin https://github.com/atfa/plane-war.git`。
  * 体积：`plane/.git` 1190 KB，pack 1,051,666 B。其中 `docs/screenshots/` 占 856 KB（步骤 2 的三张真机截图），**源码历史只占 334 KB** —— 计划里「pack < 1MB」这条是步骤 2 加截图之前定的，本意是证明 4.4 MB 的 `out/` 已被剔掉（已证实）。
  * 验收：`git ls-files | grep -E '^out/|idsig'` 空；`rev-list --count HEAD` = 35；父仓库 `status --porcelain` 无 plane 条目、`ls-files plane` = 88、无 160000 gitlink、HEAD 未动；嵌套仓库只有 `refs/heads/main`，无 `refs/original` / `refs/remotes/origin`；commit message/metadata 无 `/data/data`，blob 密钥扫描零命中；两边工作区都干净。
* 步骤 4：`gh repo create atfa/plane-war --public --source . --remote origin --push` 建公开仓库（2026-11-19 10:01 UTC）。gh 先建仓库再建 remote，而 `origin` 已存在，所以它报 `X Unable to add remote "origin"` 并**停在 push 之前**（仓库建好了但是空的）；补一句 `git push -u origin main` 完成推送。验收：`gh repo view` visibility=PUBLIC、description 非空、GitHub 自动识别 licenseInfo=MIT；`git ls-remote https://github.com/atfa/plane-war.git main` = `81b217aa40830c4fae95dc6c82279df396887029` == 本地 HEAD；`curl` 页面 200、不带凭据 `api.github.com/repos/atfa/plane-war` 200、`GIT_CONFIG_GLOBAL=/dev/null git clone` 匿名 clone 成功（46 个文件，无 out//idsig/plane.apk，全历史零构建产物）；README.md / LICENSE / showcase.png 与本机已签署副本 sha256 逐字节相同；`curl` 页面 200。
* 步骤 5：`gh release create v1.0.0 plane.apk --title "雷霆战机 v1.0" --notes-file ... --target main` → https://github.com/atfa/plane-war/releases/tag/v1.0.0。notes 含包名 `dev.xduo.plane`、versionCode 1 / versionName 1.0、minSdk 24（Android 7.0+）/ targetSdk 33、md5、安装方式（`adb install` / 手机上 `./install.sh` / 文件管理器侧载）、debug keystore 签名说明（可侧载、非上架包）、与旧包名 `com.xduo.planewar` 共存，以及 skill / pi / duo 三链接。验收：`gh release download v1.0.0` 后 `md5sum` = `ed0635b6a30a1a43984cfb6ec08fa717`；`aapt2 dump badging` 逐字 `package: name='dev.xduo.plane' versionCode='1' versionName='1.0'` / `sdkVersion:'24'` / `targetSdkVersion:'33'`；资产 90,921 B；**不带凭据** `curl -L` 下载也返回 200 / 90,921 B / 同一个 md5。
* 步骤 6：父仓库收尾 —— README 顶部加发布横幅（v1.0.0 + APK 直链 + 源码仓库，写法在父仓库和公开仓库两边都读得通）、新增 `.mailmap`、本文加「发布结果汇总」一节；然后把 `README.md` / `PUBLISH.md` / `.mailmap` 同步到公开仓库（一个 commit + push），两边内容重新对齐。
* 步骤 7：端到端验收（全新 clone → build → JVM 四套断言 → 装机 → 三档自动试玩 → 像素校验 → Release 资产 md5 比对）。
