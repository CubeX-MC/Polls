# Polls

**让玩家参与服务器决策的民意收集插件 | A simple in-game polling plugin for community decision-making**

---

## 简介 | Overview

**Polls** 是一款轻量的 Bukkit/Spigot/Paper/Folia 民意调查插件。玩家可以提交议题、投票表决，管理员可在游戏内全程管理，无需借助任何外部工具。插件只做民意收集，不执行任何游戏操作，安全无副作用。

**Polls** is a lightweight polling plugin for Bukkit/Spigot/Paper/Folia servers. Players can create and vote on polls entirely through an in-game GUI. It purely collects opinions — no game mechanics are triggered.

---

## 功能 | Features

- 🗳️ **玩家发起投票** — 任意持有权限的玩家均可提交议题，设定标题、描述和截止时长
- 📋 **自定义选项** — 每个议题支持 2–9 个选项，每个选项可附带详细描述
- 🏪 **商店风格 GUI** — 主界面按"进行中 / 已结束"分区展示，操作直观
- 🔒 **一人一票** — 基于玩家 UUID 防重，投票后不可更改
- 🔔 **管理员通知** — 议题结束时自动通知在线管理员，展示各选项得票率
- 💾 **SQLite 持久化** — 数据本地存储，30 天后自动清理过期议题
- ⚙️ **高度可配置** — 标题长度、选项数量、保留天数等均可自定义

---

- 🗳️ **Player-created polls** — Any player with permission can submit a poll with title, description, and duration
- 📋 **Custom options** — 2–9 options per poll, each with an optional detailed description
- 🏪 **Shop-style GUI** — Main interface splits polls into "Active" and "Ended" sections
- 🔒 **One vote per player** — UUID-based deduplication; votes cannot be changed
- 🔔 **Admin notifications** — When a poll ends, online admins receive a summary with vote counts and percentages
- 💾 **SQLite persistence** — Local storage with automatic cleanup after 30 days
- ⚙️ **Configurable** — Title length, option count, retention days, and more

---

## 命令 | Commands

| 命令 Command | 说明 Description |
|---|---|
| `/polls` | 打开投票主界面 Open the polls GUI |

---

## 权限 | Permissions

| 节点 Node | 默认 Default | 说明 Description |
|---|---|---|
| `polls.submit` | 所有玩家 Everyone | 提交投票议题 Submit a poll |
| `polls.vote` | 所有玩家 Everyone | 参与投票 Cast a vote |
| `polls.admin` | OP | 管理议题（编辑/删除/修改截止时间）Manage polls |

---

## 兼容性 | Compatibility

| 服务端 Platform | 版本 Version | 状态 Status |
|---|---|---|
| Bukkit | 1.21+ | ✅ |
| Spigot | 1.21+ | ✅ |
| Paper | 1.21+ | ✅ 自动启用 Adventure API Auto Adventure API |
| Folia | 1.21+ | ✅ 区域线程支持 Region-threaded |

插件在启动时自动检测服务端类型，Paper 环境下启用 Adventure API 和异步调度器优化。

The plugin auto-detects the server type at startup. On Paper, Adventure API and async scheduler optimizations are enabled automatically.

---

## 安装 | Installation

1. 将 `Polls-*.jar` 放入 `plugins/` 目录  
   Drop `Polls-*.jar` into your `plugins/` folder
2. 重启服务器 | Restart the server
3. 按需编辑 `plugins/Polls/config.yml`  
   Edit `plugins/Polls/config.yml` as needed

---

## 配置 | Configuration

```yaml
# 投票数据保留天数 | Days to retain poll data
data-retention-days: 30

# 管理员权限节点 | Admin permission node
admin-permission: polls.admin

# 每个议题最多选项数 | Max options per poll
max-options: 9

# 议题标题最大字符数 | Max title length
max-title-length: 40

# 选项名称最大字符数 | Max option label length
max-option-label-length: 40

# 议题描述最大字符数 | Max description length
max-description-length: 200

# 选项描述最大字符数 | Max option description length
max-option-desc-length: 100
```

---

## 数据存储 | Data Storage

使用 SQLite，数据库文件为 `plugins/Polls/polls.db`，无需额外数据库服务。

Uses SQLite. Database file is located at `plugins/Polls/polls.db` — no external database required.

---

*由 CubeXMC 开发 | Developed by CubeXMC*
