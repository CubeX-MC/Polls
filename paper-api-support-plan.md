# Paper API 支持实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让插件同时兼容 Bukkit/Spigot/Paper/Folia，Paper 环境下自动启用 Adventure API（富文本）和异步调度器优化

**Architecture:** 适配器模式 + 运行时检测。启动时检测 Paper 类是否存在，动态选择 PaperAdapter（Adventure Component + Paper 异步调度器）或 BukkitAdapter（ChatColor + 标准调度器）。所有消息发送和异步任务通过适配器接口统一调用。

**Tech Stack:** 
- Paper API 1.21.4（软依赖，optional）
- Adventure API（Paper 自带）
- 反射检测（`Class.forName`）

## Global Constraints

- Java 21
- Maven 构建
- 保持 Folia 支持（`folia-supported: true` 不变）
- 单个 JAR 通用，无需用户选择版本
- Commit 消息使用口语化中文风格
- 插件版本号递增到 1.0.9

---

### Task 1: 添加 Paper API 依赖

**Covers:** [S3]

**Files:**
- Modify: `pom.xml:29-48`

**Interfaces:**
- Consumes: 无
- Produces: Paper API 类在编译期可用（`io.papermc.paper.*`）

- [ ] **Step 1: 在 pom.xml dependencies 块中添加 paper-api 软依赖**

在 `folia-api` dependency 后添加：

```xml
<dependency>
    <groupId>io.papermc.paper</groupId>
    <artifactId>paper-api</artifactId>
    <version>1.21.4-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
    <optional>true</optional>
</dependency>
```

- [ ] **Step 2: 更新插件版本号到 1.0.9**

修改 `pom.xml` 第 9 行：

```xml
<version>1.0.9</version>
```

- [ ] **Step 3: 验证依赖配置**

运行：`mvn dependency:tree -Dverbose`
预期输出：包含 `io.papermc.paper:paper-api:jar:1.21.4-R0.1-SNAPSHOT:provided (optional)`

- [ ] **Step 4: 提交**

```bash
git add pom.xml
git commit -m "添加 Paper API 软依赖，版本更新到 1.0.9"
```

---

### Task 2: 创建平台适配器接口

**Covers:** [S2]

**Files:**
- Create: `src/main/java/com/polls/platform/PlatformAdapter.java`

**Interfaces:**
- Consumes: 无
- Produces: `PlatformAdapter` 接口，包含：
  - `void sendMessage(Player player, String legacyText)` - 发送消息（支持 `&` 颜色代码）
  - `void runAsync(Runnable task)` - 异步执行任务
  - `String getPlatformName()` - 获取平台名称（用于日志）

- [ ] **Step 1: 创建接口文件**

```java
package com.polls.platform;

import org.bukkit.entity.Player;

/**
 * 平台适配器接口，屏蔽不同服务端实现差异
 */
public interface PlatformAdapter {
    
    /**
     * 向玩家发送消息（支持 & 颜色代码）
     * @param player 目标玩家
     * @param legacyText 传统格式文本（&a, &7 等）
     */
    void sendMessage(Player player, String legacyText);
    
    /**
     * 异步执行任务
     * @param task 要执行的任务
     */
    void runAsync(Runnable task);
    
    /**
     * 获取当前平台名称
     * @return "Paper" 或 "Bukkit/Spigot/Folia"
     */
    String getPlatformName();
}
```

- [ ] **Step 2: 验证编译**

运行：`mvn clean compile`
预期输出：`BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/polls/platform/PlatformAdapter.java
git commit -m "创建平台适配器接口"
```

---

### Task 3: 实现 Paper 适配器

**Covers:** [S2]

**Files:**
- Create: `src/main/java/com/polls/platform/PaperAdapter.java`

**Interfaces:**
- Consumes: `PlatformAdapter` 接口（Task 2）
- Produces: `PaperAdapter` 类，构造器签名：`PaperAdapter(JavaPlugin plugin)`

- [ ] **Step 1: 创建 PaperAdapter 实现**

```java
package com.polls.platform;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper 服务器适配器，使用 Adventure API 和 Paper 异步调度器
 */
public class PaperAdapter implements PlatformAdapter {
    
    private final JavaPlugin plugin;
    private final AsyncScheduler asyncScheduler;
    
    public PaperAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
        this.asyncScheduler = Bukkit.getAsyncScheduler();
    }
    
    @Override
    public void sendMessage(Player player, String legacyText) {
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(legacyText);
        player.sendMessage(component);
    }
    
    @Override
    public void runAsync(Runnable task) {
        asyncScheduler.runNow(plugin, scheduledTask -> task.run());
    }
    
    @Override
    public String getPlatformName() {
        return "Paper";
    }
}
```

- [ ] **Step 2: 验证编译**

运行：`mvn clean compile`
预期输出：`BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/polls/platform/PaperAdapter.java
git commit -m "实现 Paper 平台适配器"
```

---

### Task 4: 实现 Bukkit 降级适配器

**Covers:** [S2]

**Files:**
- Create: `src/main/java/com/polls/platform/BukkitAdapter.java`

**Interfaces:**
- Consumes: `PlatformAdapter` 接口（Task 2）
- Produces: `BukkitAdapter` 类，构造器签名：`BukkitAdapter(JavaPlugin plugin)`

- [ ] **Step 1: 创建 BukkitAdapter 实现**

```java
package com.polls.platform;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit/Spigot/Folia 适配器，使用传统 ChatColor
 */
public class BukkitAdapter implements PlatformAdapter {
    
    private final JavaPlugin plugin;
    
    public BukkitAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void sendMessage(Player player, String legacyText) {
        String colored = ChatColor.translateAlternateColorCodes('&', legacyText);
        player.sendMessage(colored);
    }
    
    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }
    
    @Override
    public String getPlatformName() {
        return "Bukkit/Spigot/Folia";
    }
}
```

- [ ] **Step 2: 验证编译**

运行：`mvn clean compile`
预期输出：`BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/polls/platform/BukkitAdapter.java
git commit -m "实现 Bukkit 降级适配器"
```

---

### Task 5: PollsPlugin 集成适配器检测

**Covers:** [S2]

**Files:**
- Modify: `src/main/java/com/polls/PollsPlugin.java:1-20,62-67`

**Interfaces:**
- Consumes: `PlatformAdapter`, `PaperAdapter`, `BukkitAdapter`（Task 2-4）
- Produces: `PollsPlugin.getPlatformAdapter()` 方法返回 `PlatformAdapter` 实例

- [ ] **Step 1: 添加 import 和字段**

在第 5 行后添加：

```java
import com.polls.platform.PlatformAdapter;
import com.polls.platform.PaperAdapter;
import com.polls.platform.BukkitAdapter;
```

在第 17 行后添加：

```java
private PlatformAdapter platformAdapter;
```

- [ ] **Step 2: 在 onEnable 中添加平台检测逻辑**

在第 22 行（`getDataFolder().mkdirs();`）后添加：

```java
// 检测并初始化平台适配器
try {
    Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
    platformAdapter = new PaperAdapter(this);
    getLogger().info("检测到 Paper 服务器，启用 Adventure API 支持");
} catch (ClassNotFoundException e) {
    platformAdapter = new BukkitAdapter(this);
    getLogger().info("使用 Bukkit/Spigot/Folia 兼容模式");
}
```

- [ ] **Step 3: 添加 getter 方法**

在第 66 行后添加：

```java
public PlatformAdapter getPlatformAdapter() { return platformAdapter; }
```

- [ ] **Step 4: 验证编译**

运行：`mvn clean compile`
预期输出：`BUILD SUCCESS`

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/polls/PollsPlugin.java
git commit -m "在插件启动时检测并初始化平台适配器"
```

---

### Task 6: 重构 PollScheduler 使用适配器发送消息

**Covers:** [S4]

**Files:**
- Modify: `src/main/java/com/polls/PollScheduler.java:50-66`

**Interfaces:**
- Consumes: `PollsPlugin.getPlatformAdapter()` 返回 `PlatformAdapter`（Task 5）
- Produces: 无

- [ ] **Step 1: 修改 notifyAdmins 方法使用适配器**

替换第 50-66 行的 `notifyAdmins` 方法：

```java
private void notifyAdmins(Poll poll) {
    int totalVotes = poll.getOptions().stream().mapToInt(o -> o.getVoteCount()).sum();
    StringBuilder sb = new StringBuilder();
    sb.append("&8[&6Polls&8] &e议题已结束: &f").append(poll.getTitle())
      .append(" &8| &7总票数: &f").append(totalVotes).append(" &8|");
    poll.getOptions().forEach(opt -> {
        double pct = totalVotes > 0 ? (opt.getVoteCount() * 100.0 / totalVotes) : 0;
        sb.append(" &a").append(opt.getLabel()).append(": &f").append(opt.getVoteCount())
          .append(" &7(").append(String.format("%.1f", pct)).append("%)");
    });
    String msg = sb.toString();
    for (Player p : Bukkit.getOnlinePlayers()) {
        if (p.hasPermission(plugin.getAdminPermission())) {
            plugin.getPlatformAdapter().sendMessage(p, msg);
        }
    }
}
```

- [ ] **Step 2: 验证编译**

运行：`mvn clean compile`
预期输出：`BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/polls/PollScheduler.java
git commit -m "PollScheduler 改用平台适配器发送通知消息"
```

---

### Task 7: 重构 PollsPlugin 命令反馈使用适配器

**Covers:** [S4]

**Files:**
- Modify: `src/main/java/com/polls/PollsPlugin.java:52-60`

**Interfaces:**
- Consumes: `PollsPlugin.getPlatformAdapter()` 返回 `PlatformAdapter`（Task 5）
- Produces: 无

- [ ] **Step 1: 修改 onCommand 中的错误消息**

替换第 55 行：

```java
platformAdapter.sendMessage((Player) sender, "&c只有玩家才能使用此命令。");
```

但由于 `sender` 在此处不是 `Player`，需要保持原样。将第 55-56 行改为：

```java
if (!(sender instanceof Player player)) {
    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c只有玩家才能使用此命令。"));
    return true;
}
```

并添加 import：

```java
import org.bukkit.ChatColor;
```

（注意：控制台消息不通过适配器，因为 sender 不是 Player）

- [ ] **Step 2: 验证编译**

运行：`mvn clean compile`
预期输出：`BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/polls/PollsPlugin.java
git commit -m "命令反馈消息规范化（控制台消息保持 ChatColor）"
```

---

### Task 8: GuiUtils 保持 Paper API 兼容（已完成，仅验证）

**Covers:** [S4]

**Files:**
- Verify: `src/main/java/com/polls/gui/GuiUtils.java`

**Interfaces:**
- Consumes: 无
- Produces: 无

- [ ] **Step 1: 检查现有代码**

阅读 `GuiUtils.java`，确认：
- 第 24 行使用 `LegacyComponentSerializer.legacyAmpersand().deserialize(name)`
- 第 26 行使用 `LegacyComponentSerializer.legacySection().deserialize(l)`
- 已经使用 Adventure API，Paper 环境下自动生效

- [ ] **Step 2: 验证编译**

运行：`mvn clean compile`
预期输出：`BUILD SUCCESS`

- [ ] **Step 3: 记录验证结果**

在终端输出：`GuiUtils 已使用 Adventure API，无需修改`

---

### Task 9: 更新 plugin.yml API 版本声明

**Covers:** [S3]

**Files:**
- Modify: `src/main/resources/plugin.yml:4-5`

**Interfaces:**
- Consumes: 无
- Produces: 无

- [ ] **Step 1: 添加 Paper API 版本注释**

在第 4-5 行之间添加注释：

```yaml
api-version: '1.21'
# Paper API 作为软依赖，运行时自动检测并启用
folia-supported: true
```

- [ ] **Step 2: 验证构建**

运行：`mvn clean package`
预期输出：
- `BUILD SUCCESS`
- 生成 `target/Polls-1.0.9.jar`

- [ ] **Step 3: 提交**

```bash
git add src/main/resources/plugin.yml
git commit -m "添加 Paper API 软依赖说明注释"
```

---

### Task 10: 集成测试和文档更新

**Covers:** [S1]

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: 所有前置任务的产出
- Produces: 更新的文档

- [ ] **Step 1: 手动测试验证（在 Paper 服务器）**

1. 复制 `target/Polls-1.0.9.jar` 到 Paper 1.21.4 测试服务器
2. 启动服务器，检查日志是否显示：`检测到 Paper 服务器，启用 Adventure API 支持`
3. 执行 `/polls` 命令，确认 GUI 正常显示
4. 创建议题并投票，确认功能正常
5. 等待议题结束，确认管理员通知消息正常（彩色文本）

- [ ] **Step 2: 手动测试验证（在 Spigot/Bukkit 服务器）**

1. 复制同一个 JAR 到 Spigot 1.21.4 测试服务器
2. 启动服务器，检查日志是否显示：`使用 Bukkit/Spigot/Folia 兼容模式`
3. 重复步骤 1.3-1.5，确认降级模式下功能正常

- [ ] **Step 3: 更新 README.md 兼容性说明**

在 README.md 的"特性"部分后添加：

```markdown
## 服务端兼容性

- ✅ Bukkit 1.21+
- ✅ Spigot 1.21+
- ✅ Paper 1.21+（自动启用 Adventure API 和性能优化）
- ✅ Folia 1.21+（区域线程支持）

插件会在运行时自动检测服务端类型，Paper 环境下自动使用 Adventure API 和异步调度器优化。
```

- [ ] **Step 4: 提交**

```bash
git add README.md
git commit -m "更新文档：添加多服务端兼容性说明"
```

- [ ] **Step 5: 创建版本标签**

```bash
git tag v1.0.9
git push origin main --tags
```

预期：触发 GitHub Actions 构建并发布 v1.0.9 版本

---

## 实现完成标准

- [ ] 所有 10 个任务的步骤全部完成
- [ ] `mvn clean package` 构建成功
- [ ] Paper 服务器上测试通过（检测日志 + 功能验证）
- [ ] Bukkit/Spigot 服务器上测试通过（降级模式验证）
- [ ] v1.0.9 tag 已推送，GitHub Actions 已触发构建
