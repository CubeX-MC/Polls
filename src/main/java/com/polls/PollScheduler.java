package com.polls;

import com.polls.model.Poll;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PollScheduler {

    private final PollsPlugin plugin;
    private final Map<Integer, Long> notifiedEndTimes = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> lastActiveStates = new ConcurrentHashMap<>();
    private final AtomicBoolean maintenanceRunning = new AtomicBoolean();

    public PollScheduler(PollsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        rememberPreviouslyEndedPolls();
        plugin.getPlatformAdapter().runRepeating(this::scheduleMaintenance,
                20L * 60, 20L * 60);
    }

    private void rememberPreviouslyEndedPolls() {
        for (Poll poll : plugin.getPollCache().getAll()) {
            boolean active = poll.isActive();
            lastActiveStates.put(poll.getId(), active);
            if (!active) {
                notifiedEndTimes.put(poll.getId(), poll.getEndsAt());
            }
        }
    }

    private void scheduleMaintenance() {
        if (!maintenanceRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            plugin.getPlatformAdapter().runAsync(() -> {
                try {
                    runMaintenanceAsync();
                } catch (RuntimeException e) {
                    maintenanceRunning.set(false);
                    plugin.getLogger().warning("议题维护任务失败: " + e.getMessage());
                }
            });
        } catch (RuntimeException e) {
            maintenanceRunning.set(false);
            plugin.getLogger().warning("启动议题维护任务失败: " + e.getMessage());
        }
    }

    /**
     * SQLite 读写全部在异步线程完成。只有缓存替换、通知和过期删除后的
     * 内存更新回到全局线程，避免阻塞 Bukkit 主线程或 Folia 全局区。
     */
    private void runMaintenanceAsync() {
        List<Poll> polls = plugin.getPollCache().getAll();
        int retentionDays = Math.max(1, plugin.getConfig().getInt("data-retention-days", 30));
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);

        List<Integer> expiredCandidates = new ArrayList<>();
        for (Poll poll : polls) {
            if (poll.getEndsAt() < cutoff) {
                expiredCandidates.add(poll.getId());
            }
        }

        int deleted = 0;
        boolean deleteSucceeded = true;
        try {
            deleted = plugin.getDatabase().deleteExpiredPolls(cutoff);
        } catch (SQLException e) {
            // Do not remove cache entries if the delete did not complete.
            deleteSucceeded = false;
            plugin.getLogger().severe("清理过期议题失败: " + e.getMessage());
        }

        // Confirm IDs against the database after the DELETE. An administrator may
        // extend a poll between the cache snapshot and the delete statement.
        List<Integer> expiredIds = new ArrayList<>();
        if (deleteSucceeded) {
            for (int pollId : expiredCandidates) {
                try {
                    if (plugin.getDatabase().loadPoll(pollId) == null) {
                        expiredIds.add(pollId);
                    }
                } catch (SQLException e) {
                    plugin.getLogger().warning("确认过期议题删除状态失败，将在下次维护时重试: "
                            + e.getMessage());
                }
            }
        }

        Set<Integer> expiredIdSet = new HashSet<>(expiredIds);
        List<Poll> refreshed = new ArrayList<>();
        List<Poll> endedToNotify = new ArrayList<>();
        for (Poll poll : polls) {
            if (expiredIdSet.contains(poll.getId())) {
                continue;
            }
            boolean active = poll.isActive();
            Boolean previousActive = lastActiveStates.put(poll.getId(), active);
            boolean transitionedToEnded = Boolean.TRUE.equals(previousActive) && !active;
            if (active) {
                notifiedEndTimes.remove(poll.getId());
                continue;
            }

            Long notifiedEndTime = notifiedEndTimes.get(poll.getId());
            if (!transitionedToEnded
                    && notifiedEndTime != null
                    && notifiedEndTime.longValue() == poll.getEndsAt()) {
                continue;
            }

            try {
                Poll fresh = plugin.getDatabase().loadPoll(poll.getId());
                if (fresh == null) {
                    notifiedEndTimes.remove(poll.getId());
                    continue;
                }
                refreshed.add(fresh);
                if (fresh.isActive()) {
                    lastActiveStates.put(fresh.getId(), true);
                    notifiedEndTimes.remove(fresh.getId());
                    continue;
                }
                lastActiveStates.put(fresh.getId(), false);
                Long freshNotifiedEndTime = notifiedEndTimes.get(fresh.getId());
                if (freshNotifiedEndTime == null
                        || freshNotifiedEndTime.longValue() != fresh.getEndsAt()) {
                    endedToNotify.add(fresh);
                }
            } catch (SQLException e) {
                // Keep the entry unmarked so a transient database failure retries next cycle.
                plugin.getLogger().warning("加载议题数据失败，将在下次维护时重试: " + e.getMessage());
            }
        }

        Set<Integer> currentIds = polls.stream()
                .map(Poll::getId)
                .filter(id -> !expiredIdSet.contains(id))
                .collect(Collectors.toSet());
        int deletedCount = deleted;
        try {
            plugin.getPlatformAdapter().runGlobal(() -> {
                try {
                    for (int pollId : expiredIds) {
                        plugin.getPollCache().removePoll(pollId);
                        notifiedEndTimes.remove(pollId);
                        lastActiveStates.remove(pollId);
                    }
                    for (Poll fresh : refreshed) {
                        plugin.getPollCache().updatePoll(fresh);
                    }
                    for (Poll ended : endedToNotify) {
                        if (plugin.getPollCache().getById(ended.getId()) == null) {
                            // The poll may have been deleted while the async read was running.
                            notifiedEndTimes.remove(ended.getId());
                            continue;
                        }
                        try {
                            notifyAdmins(ended);
                            // Mark only after the fresh result was successfully dispatched.
                            notifiedEndTimes.put(ended.getId(), ended.getEndsAt());
                        } catch (RuntimeException e) {
                            plugin.getLogger().warning("通知议题结束结果失败，将在下次维护时重试: "
                                    + e.getMessage());
                        }
                    }
                    notifiedEndTimes.keySet().retainAll(currentIds);
                    lastActiveStates.keySet().retainAll(currentIds);
                    if (deletedCount > 0) {
                        plugin.getLogger().info("清理过期议题 " + deletedCount + " 条");
                    }
                } finally {
                    maintenanceRunning.set(false);
                }
            });
        } catch (RuntimeException e) {
            maintenanceRunning.set(false);
            plugin.getLogger().warning("调度议题维护结果失败: " + e.getMessage());
        }
    }

    private void notifyAdmins(Poll poll) {
        int totalVotes = poll.getOptions().stream().mapToInt(o -> o.getVoteCount()).sum();
        StringBuilder sb = new StringBuilder();
        sb.append("&8[&6Polls&8] &e议题已结束: &f").append(poll.getTitle())
          .append(" &8| &7总票数: &f").append(totalVotes).append(" &8|");
        poll.getOptions().forEach(opt -> {
            double pct = totalVotes > 0 ? (opt.getVoteCount() * 100.0 / totalVotes) : 0;
            sb.append(" &a").append(opt.getLabel()).append(": &f").append(opt.getVoteCount())
              .append(" &7(").append(String.format(java.util.Locale.ROOT, "%.1f", pct)).append("%)");
        });
        String msg = sb.toString();
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.getPlatformAdapter().runForPlayer(p, () -> {
                if (p.isOnline() && p.hasPermission(plugin.getAdminPermission())) {
                    plugin.getPlatformAdapter().sendMessage(p, msg);
                }
            });
        }
    }

}
