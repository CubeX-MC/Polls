# Polls

> A lightweight in-game polling plugin for Bukkit, Spigot, Paper, and Folia.
>
> 轻量、直观的游戏内民意投票插件，支持 Bukkit、Spigot、Paper 和 Folia。

Players can browse polls, inspect live results, and vote through an inventory GUI. Poll creation and administration are handled entirely in game, with no web panel or external database required.

玩家可以通过 GUI 浏览议题、查看实时票数并参与投票。议题创建与管理也可在游戏内完成，不需要网页面板或外部数据库。

**Requirements / 运行要求:** Minecraft `1.21.4+` · Java `21+`

[Source / 源代码](https://github.com/CubeX-MC/Polls) · [Issue tracker / 问题反馈](https://github.com/CubeX-MC/Polls/issues)

---

## Features / 核心功能

- **Private guided input:** Chat messages used to create or edit a poll are captured by the plugin and never sent to public chat.<br>
  **私密引导输入：** 创建或编辑议题时，聊天框输入会被插件捕获，不会发送到公共聊天。
- **Fully in-game workflow:** Create, browse, vote, and manage polls through guided chat input and inventory GUIs.<br>
  **完整游戏内流程：** 创建、浏览、投票和管理都通过聊天引导与 GUI 完成。
- **One player, one vote:** UUID-based vote tracking prevents duplicate votes and vote changes.<br>
  **一人一票：** 使用玩家 UUID 防止重复投票，投票后不可更改。
- **Live results:** The detail view shows vote counts, percentages, progress bars, and the player's own choice.<br>
  **实时结果：** 详情界面展示票数、百分比、进度条以及玩家自己的选择。
- **Flexible polls:** Each poll supports 2 to 9 options with a title, description, option details, and duration.<br>
  **灵活议题：** 每个议题支持 2 至 9 个选项，可设置标题、描述、选项说明和截止时长。
- **Administration tools:** Administrators can edit the title or description, extend the deadline, or delete a poll.<br>
  **管理员工具：** 管理员可修改标题、描述、截止时间，或删除议题。
- **End notifications:** Online administrators receive a result summary when a poll ends.<br>
  **结束通知：** 议题结束后，在线管理员会收到票数和占比摘要。
- **Local persistence:** SQLite stores poll data locally and automatically removes expired records according to the configuration.<br>
  **本地持久化：** 使用 SQLite 保存数据，并按配置自动清理过期记录。
- **Multi-platform scheduling:** Paper and Folia use region-aware scheduling APIs, while Bukkit and Spigot use the compatible scheduler.<br>
  **多平台调度：** Paper 和 Folia 使用区域调度 API，Bukkit 与 Spigot 使用兼容调度器。
- **Built-in languages:** Switch between English (`en_US`) and Simplified Chinese (`zh_CN`) in `config.yml`.<br>
  **内置语言：** 可在 `config.yml` 中切换英文（`en_US`）和简体中文（`zh_CN`）。
- **Creation templates:** Start a normal, rental, or loan proposal with localized approve/reject choices prefilled for the latter two.<br>
  **创建模板：** 可选择普通、出租或贷款模板，后两者会预填本地化的同意/拒绝选项。

These templates only prefill a poll. They do not transfer items, charge money, or create a rental/loan contract.<br>
这些模板只预填投票内容，不会自动转移物品、扣款或创建出租/贷款合同。

---

## Screenshots / 界面预览

### Poll browser / 议题列表

![Polls main interface](https://raw.githubusercontent.com/CubeX-MC/Polls/main/images/20260714-045849.png)

Active and ended polls are displayed in separate sections with independent pagination.

进行中与已结束议题分区展示，并分别支持翻页。

### Poll details and results / 议题详情与结果

![Poll details and voting results](https://raw.githubusercontent.com/CubeX-MC/Polls/main/images/20260714-045852.jpg)

Players can inspect option descriptions, live counts, percentages, and their own vote.

玩家可以查看选项说明、实时票数、百分比以及自己的投票记录。

### Option editor / 选项管理

![Poll option editor](https://raw.githubusercontent.com/CubeX-MC/Polls/main/images/20260714-045843.jpg)

Poll creators can add, review, or remove options before submitting the poll. At least two options are required.

创建议题时可添加、查看或移除选项，至少需要两个选项。

---

## Workflow / 使用流程

### Vote / 参与投票

1. Run `/polls` to open the main interface.
2. Select an active or ended poll.
3. Click an option to vote, or inspect the final results.

中文说明：输入 `/polls` 打开主界面，选择议题后即可投票或查看最终结果。

### Create a poll / 创建议题

1. Click **Create Poll** in the bottom-right corner of the main interface.
2. Choose the normal, rental, or loan template.
3. Enter the title, description, and duration in chat. Supported examples include `30m`, `12h`, and `3d`.
4. Add, remove, or review 2 to 9 options. Rental and loan templates start with approve/reject choices.
5. Confirm the poll in the option editor.

The guided setup captures every input privately before publishing the poll.

中文说明：点击主界面右下角的“提交新议题”，先选择普通、出租或贷款模板，再按聊天提示填写标题、描述、截止时长与选项，最后在选项管理界面确认提交。所有输入均为私密输入。

---

## Commands / 命令

| Command / 命令 | Description / 说明 |
|---|---|
| `/polls` | Open the polls GUI / 打开投票主界面 |

## Permissions / 权限

| Permission / 权限 | Default / 默认值 | Description / 说明 |
|---|---|---|
| `polls.submit` | Everyone / 所有玩家 | Create polls / 创建投票议题 |
| `polls.vote` | Everyone / 所有玩家 | Cast votes / 参与投票 |
| `polls.admin` | OP | Edit, extend, or delete polls / 编辑、延期或删除议题 |

The administrator permission node can be changed in `config.yml`.

管理员权限节点可在 `config.yml` 中修改。

---

## Compatibility / 兼容性

| Platform / 平台 | Version / 支持版本 | Runtime / 运行方式 |
|---|---|---|
| Bukkit | `1.21.4+` | Compatible Bukkit scheduler / Bukkit 兼容调度器 |
| Spigot | `1.21.4+` | Compatible Bukkit scheduler / Bukkit 兼容调度器 |
| Paper | `1.21.4+` | Adventure API and region schedulers / Adventure API 与区域调度器 |
| Folia | `1.21.4+` | Native entity, global, and async schedulers / 原生实体、全局与异步调度器 |

The platform adapter is selected automatically. On Folia, the plugin fails safely instead of falling back to an incompatible Bukkit scheduler.

插件会自动选择平台适配器。在 Folia 环境中，如果适配器初始化失败，插件会安全停止，而不会回退到不兼容的 Bukkit 调度器。

---

## Installation / 安装

1. Download a `Polls-*.jar` compatible with your server version.
2. Place the JAR in the server's `plugins/` directory.
3. Restart the server.
4. Edit `plugins/Polls/config.yml` if needed, then restart the server to apply the changes.

中文说明：下载 JAR 并放入 `plugins/` 目录后重启服务器。修改生成的配置文件后，再次重启服务器使配置生效。

---

## Configuration / 配置

```yaml
# Interface language: en_US or zh_CN / 界面语言
language: zh_CN

# Poll creation templates / 创建模板
submit-templates:
  enabled: true
  available: [normal, rental, loan]
  prefill-options: true

# Days to retain ended polls / 投票结束后保留数据的天数
data-retention-days: 30

# Administrator permission node / 管理员权限节点
admin-permission: polls.admin

# Maximum options per poll, valid range: 2-9 / 每个议题最多选项数
max-options: 9

# Text length limits / 文本长度限制
max-title-length: 40
max-option-label-length: 40
max-description-length: 200
max-option-desc-length: 100
```

Editable language files are created at `plugins/Polls/lang/zh_CN.yml` and `plugins/Polls/lang/en_US.yml` on first startup. Existing installations receive `language: zh_CN` automatically; restart the server after changing it.<br>
首次启动后会生成可编辑的 `plugins/Polls/lang/zh_CN.yml` 与 `plugins/Polls/lang/en_US.yml`。旧版本升级时会自动补入 `language: zh_CN`；修改后请重启服务器。
Set `submit-templates.enabled` to `false` to skip the template menu, or narrow `available` to the templates you want to show.<br>
将 `submit-templates.enabled` 设为 `false` 可跳过模板菜单，也可以通过 `available` 限制显示的模板。

---

## Data and notifications / 数据与通知

- Poll data is stored in `plugins/Polls/polls.db`; MySQL or another database service is not required.<br>
  投票数据保存在本地 SQLite 文件中，不需要 MySQL 或其他数据库服务。
- Creating and voting on polls does not broadcast messages to the whole server.<br>
  议题创建和投票过程不会向全服广播。
- Only online administrators receive a result summary when a poll ends.<br>
  议题结束时，仅在线管理员会收到结果摘要。
- Polls older than `data-retention-days` are removed automatically.<br>
  超过配置保留时间的议题会自动清理。
- bStats is used for anonymous usage metrics. Poll content and vote records are never uploaded.<br>
  插件使用 bStats 收集匿名使用统计，不会上传议题内容或投票记录。

---

Polls collects and displays community feedback only. It never executes commands or changes gameplay automatically based on a result.

Polls 只负责收集和展示玩家意见，不会根据投票结果自动执行服务器命令或修改游戏内容。

*Developed by CubeXMC / 由 CubeXMC 开发*
