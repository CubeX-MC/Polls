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

    public PollCache(PollsPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void reload() {
        try {
            List<Poll> polls = plugin.getDatabase().loadAllPolls();
            cache = new CopyOnWriteArrayList<>(polls);
        } catch (SQLException e) {
            plugin.getLogger().severe("加载议题缓存失败: " + e.getMessage());
        }
    }

    public synchronized void addPoll(Poll poll) {
        cache.add(poll);
    }

    public synchronized void removePoll(int pollId) {
        cache.removeIf(p -> p.getId() == pollId);
    }

    public synchronized void updatePoll(Poll updated) {
        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).getId() == updated.getId()) {
                cache.set(i, updated);
                return;
            }
        }
    }

    public List<Poll> getAll() {
        return new ArrayList<>(cache);
    }

    public Poll getById(int id) {
        return cache.stream().filter(p -> p.getId() == id).findFirst().orElse(null);
    }
}
