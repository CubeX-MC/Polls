package com.polls.db;

import com.polls.PollsPlugin;
import com.polls.model.Poll;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PollCache {

    private final PollsPlugin plugin;
    private final CopyOnWriteArrayList<Poll> cache = new CopyOnWriteArrayList<>();

    public PollCache(PollsPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        try {
            List<Poll> polls = plugin.getDatabase().loadAllPolls();
            cache.clear();
            cache.addAll(polls);
        } catch (SQLException e) {
            plugin.getLogger().severe("加载议题缓存失败: " + e.getMessage());
        }
    }

    public List<Poll> getAll() {
        return new ArrayList<>(cache);
    }

    public Poll getById(int id) {
        return cache.stream().filter(p -> p.getId() == id).findFirst().orElse(null);
    }
}
