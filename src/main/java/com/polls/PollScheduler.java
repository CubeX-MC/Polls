package com.polls;

import com.polls.model.Poll;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PollScheduler {

    private final PollsPlugin plugin;
    private final Map<Integer, Long> notifiedEndTimes = new HashMap<>();

    public PollScheduler(PollsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        rememberPreviouslyEndedPolls();
        plugin.getPlatformAdapter().runRepeating(() -> {
            checkEnded();
            cleanExpired();
        }, 20L * 60, 20L * 60);
    }

    private void rememberPreviouslyEndedPolls() {
        for (Poll poll : plugin.getPollCache().getAll()) {
            if (!poll.isActive()) {
                notifiedEndTimes.put(poll.getId(), poll.getEndsAt());
            }
        }
    }

    private void checkEnded() {
        List<Poll> polls = plugin.getPollCache().getAll();
        for (Poll poll : polls) {
            if (poll.isActive()) {
                notifiedEndTimes.remove(poll.getId());
                continue;
            }

            Long notifiedEndTime = notifiedEndTimes.get(poll.getId());
            if (notifiedEndTime != null && notifiedEndTime.longValue() == poll.getEndsAt()) {
                continue;
            }

            try {
                Poll fresh = plugin.getDatabase().loadPoll(poll.getId());
                if (fresh == null) {
                    notifiedEndTimes.remove(poll.getId());
                    continue;
                }
                if (fresh.isActive()) {
                    notifiedEndTimes.remove(poll.getId());
                    continue;
                }
                Long freshNotifiedEndTime = notifiedEndTimes.get(fresh.getId());
                if (freshNotifiedEndTime != null
                        && freshNotifiedEndTime.longValue() == fresh.getEndsAt()) {
                    continue;
                }
                notifiedEndTimes.put(fresh.getId(), fresh.getEndsAt());
                notifyAdmins(fresh);
            } catch (SQLException e) {
                plugin.getLogger().warning("加载议题数据失败: " + e.getMessage());
                notifiedEndTimes.put(poll.getId(), poll.getEndsAt());
                notifyAdmins(poll);
            }
        }

        Set<Integer> currentIds = polls.stream().map(Poll::getId).collect(Collectors.toSet());
        notifiedEndTimes.keySet().retainAll(currentIds);
    }

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
            plugin.getPlatformAdapter().runForPlayer(p, () -> {
                if (p.isOnline() && p.hasPermission(plugin.getAdminPermission())) {
                    plugin.getPlatformAdapter().sendMessage(p, msg);
                }
            });
        }
    }

    private void cleanExpired() {
        int retentionDays = Math.max(1, plugin.getConfig().getInt("data-retention-days", 30));
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);
        try {
            int deleted = plugin.getDatabase().deleteExpiredPolls(cutoff);
            if (deleted > 0) {
                plugin.getPollCache().reload();
                // 清除已删除 poll 的通知记录，避免内存泄漏
                Set<Integer> activeIds = plugin.getPollCache().getAll()
                        .stream().map(Poll::getId)
                        .collect(Collectors.toSet());
                notifiedEndTimes.keySet().retainAll(activeIds);
                plugin.getLogger().info("清理过期议题 " + deleted + " 条");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("清理过期议题失败: " + e.getMessage());
        }
    }
}
