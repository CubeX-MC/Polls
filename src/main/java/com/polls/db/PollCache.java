package com.polls.db;

import com.polls.PollsPlugin;
import com.polls.model.Poll;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class PollCache {

    private final PollsPlugin plugin;
    private final AtomicReference<CopyOnWriteArrayList<Poll>> cacheRef =
            new AtomicReference<>(new CopyOnWriteArrayList<>());

    public PollCache(PollsPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        try {
            List<Poll> polls = plugin.getDatabase().loadAllPolls();
            cacheRef.set(new CopyOnWriteArrayList<>(polls));
        } catch (SQLException e) {
            plugin.getLogger().severe("加载议题缓存失败: " + e.getMessage());
        }
    }

    public void addPoll(Poll poll) {
        cacheRef.get().add(poll);
    }

    public void removePoll(int pollId) {
        cacheRef.get().removeIf(p -> p.getId() == pollId);
    }

    public void updatePoll(Poll updated) {
        CopyOnWriteArrayList<Poll> list = cacheRef.get();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == updated.getId()) {
                list.set(i, updated);
                return;
            }
        }
    }

    public List<Poll> getAll() {
        return new ArrayList<>(cacheRef.get());
    }

    public Poll getById(int id) {
        return cacheRef.get().stream().filter(p -> p.getId() == id).findFirst().orElse(null);
    }
}
