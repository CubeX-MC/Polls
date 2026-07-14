package com.polls;

import com.polls.db.Database;
import com.polls.db.PollCache;
import com.polls.model.Poll;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class PollScheduler {

    private final PollsPlugin plugin;
    private final Set<Integer> notifiedPollIds = new HashSet<>();

    public PollScheduler(PollsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, t -> {
            checkEnded();
            cleanExpired();
        }, 20L * 60, 20L * 60);
    }

    private void checkEnded() {
        List<Poll> polls = plugin.getPollCache().getAll();
        for (Poll poll : polls) {
            if (!poll.isActive() && !notifiedPollIds.contains(poll.getId())) {
                notifiedPollIds.add(poll.getId());
                notifyAdmins(poll);
            }
        }
    }

    private void notifyAdmins(Poll poll) {
        int totalVotes = poll.getOptions().stream().mapToInt(o -> o.getVoteCount()).sum();
        StringBuilder sb = new StringBuilder();
        sb.append("§8[§6Polls§8] §e议题已结束: §f").append(poll.getTitle())
          .append(" §8| §7总票数: §f").append(totalVotes).append(" §8|");
        poll.getOptions().forEach(opt -> {
            double pct = totalVotes > 0 ? (opt.getVoteCount() * 100.0 / totalVotes) : 0;
            sb.append(" §a").append(opt.getLabel()).append(": §f").append(opt.getVoteCount())
              .append(" §7(").append(String.format("%.1f", pct)).append("%)");
        });
        String msg = sb.toString();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("polls.admin")) {
                p.sendMessage(msg);
            }
        }
    }

    private void cleanExpired() {
        int retentionDays = plugin.getConfig().getInt("data-retention-days", 30);
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);
        try {
            int deleted = plugin.getDatabase().deleteExpiredPolls(cutoff);
            if (deleted > 0) {
                plugin.getPollCache().reload();
                plugin.getLogger().info("清理过期议题 " + deleted + " 条");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("清理过期议题失败: " + e.getMessage());
        }
    }
}
