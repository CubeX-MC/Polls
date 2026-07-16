package com.polls.db;

import com.polls.PollsPlugin;
import com.polls.model.Poll;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PollCache {

    private final PollsPlugin plugin;
    private volatile CopyOnWriteArrayList<Poll> cache = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    public PollCache(PollsPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void reload() {
        try {
            List<Poll> polls = plugin.getDatabase().loadAllPolls();
            cache = new CopyOnWriteArrayList<>(polls);
            notifyChanged();
        } catch (SQLException e) {
            plugin.getLogger().severe("加载议题缓存失败: " + e.getMessage());
        }
    }

    public synchronized void addPoll(Poll poll) {
        cache.add(poll);
        notifyChanged();
    }

    public synchronized void removePoll(int pollId) {
        if (cache.removeIf(p -> p.getId() == pollId)) {
            notifyChanged();
        }
    }

    public synchronized void updatePoll(Poll updated) {
        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).getId() == updated.getId()) {
                cache.set(i, updated);
                notifyChanged();
                return;
            }
        }
    }

    /**
     * 发布投票产生的新快照。票数只允许向前推进，避免并发回调把缓存覆盖成旧计数。
     */
    public synchronized void updateVoteSnapshot(Poll updated) {
        for (int i = 0; i < cache.size(); i++) {
            Poll current = cache.get(i);
            if (current.getId() != updated.getId()) continue;
            if (totalVotes(updated) < totalVotes(current)) return;
            cache.set(i, updated);
            notifyChanged();
            return;
        }
        cache.add(updated);
        notifyChanged();
    }

    public List<Poll> getAll() {
        return new ArrayList<>(cache);
    }

    public Poll getById(int id) {
        return cache.stream().filter(p -> p.getId() == id).findFirst().orElse(null);
    }

    public Runnable addChangeListener(Runnable listener) {
        changeListeners.add(listener);
        return () -> changeListeners.remove(listener);
    }

    private int totalVotes(Poll poll) {
        return poll.getOptions().stream().mapToInt(option -> option.getVoteCount()).sum();
    }

    private void notifyChanged() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                plugin.getLogger().warning("刷新投票界面失败: " + e.getMessage());
            }
        }
    }
}
