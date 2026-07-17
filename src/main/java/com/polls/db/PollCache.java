package com.polls.db;

import com.polls.PollsPlugin;
import com.polls.model.Poll;
import com.polls.model.PollOption;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class PollCache {

    private final PollsPlugin plugin;
    private volatile CopyOnWriteArrayList<Poll> cache = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    /**
     * IDs removed from the authoritative database. Late async reads must not
     * reinsert these polls during this plugin lifetime.
     */
    private final Set<Integer> deletedPollIds = new HashSet<>();

    public PollCache(PollsPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void reload() {
        try {
            List<Poll> loaded = plugin.getDatabase().loadAllPolls();

            Map<Integer, Poll> currentById = new HashMap<>();
            for (Poll poll : cache) currentById.put(poll.getId(), poll);
            List<Poll> merged = new ArrayList<>();
            for (Poll poll : loaded) {
                if (deletedPollIds.contains(poll.getId())) continue;
                Poll current = currentById.get(poll.getId());
                merged.add(current == null ? poll : mergePoll(current, poll, false));
            }
            cache = new CopyOnWriteArrayList<>(merged);
            notifyChanged();
        } catch (SQLException e) {
            plugin.getLogger().severe("加载议题缓存失败: " + e.getMessage());
        }
    }

    public synchronized void addPoll(Poll poll) {
        if (poll == null || deletedPollIds.contains(poll.getId())) return;
        for (int i = 0; i < cache.size(); i++) {
            Poll current = cache.get(i);
            if (current.getId() != poll.getId()) continue;
            cache.set(i, mergePoll(current, poll, false));
            notifyChanged();
            return;
        }
        cache.add(poll);
        notifyChanged();
    }

    public synchronized void removePoll(int pollId) {
        deletedPollIds.add(pollId);
        if (cache.removeIf(p -> p.getId() == pollId)) {
            notifyChanged();
        }
    }

    public synchronized void updatePoll(Poll updated) {
        if (updated == null || deletedPollIds.contains(updated.getId())) return;
        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).getId() == updated.getId()) {
                cache.set(i, mergePoll(cache.get(i), updated, false));
                notifyChanged();
                return;
            }
        }
    }

    /**
     * 发布投票产生的新快照。票数只允许向前推进，且保留缓存中的议题元数据，
     * 避免旧的异步读取把管理员刚修改的标题/描述覆盖回去。
     */
    public synchronized void updateVoteSnapshot(Poll updated) {
        if (updated == null || deletedPollIds.contains(updated.getId())) return;
        for (int i = 0; i < cache.size(); i++) {
            Poll current = cache.get(i);
            if (current.getId() != updated.getId()) continue;
            if (!canApplyVoteSnapshot(current, updated)) return;
            if (!hasVoteCountIncrease(current, updated)) return;
            cache.set(i, mergePoll(current, updated, true));
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

    /**
     * Merges two snapshots without allowing any option's count to move
     * backwards. The incoming snapshot supplies metadata for management
     * updates, while vote snapshots retain the current metadata.
     */
    private Poll mergePoll(Poll current, Poll incoming, boolean preserveCurrentMetadata) {
        Poll metadata = preserveCurrentMetadata ? current : incoming;
        Poll merged = new Poll(
                metadata.getId(),
                metadata.getCreator(),
                metadata.getCreatorName(),
                metadata.getTitle(),
                metadata.getDescription(),
                metadata.getCreatedAt(),
                metadata.getEndsAt()
        );

        Map<Integer, PollOption> currentOptions = byId(current);
        Set<Integer> copied = new HashSet<>();

        for (PollOption option : incoming.getOptions()) {
            PollOption currentOption = currentOptions.get(option.getId());
            int count = Math.max(option.getVoteCount(),
                    currentOption == null ? 0 : currentOption.getVoteCount());
            merged.getOptions().add(copyOption(option, count));
            copied.add(option.getId());
        }
        // Keep options that only exist in the current snapshot. Option rows are
        // not editable through the GUI, but retaining them avoids data loss if
        // an older database read races with a newer one.
        for (PollOption option : current.getOptions()) {
            if (!copied.contains(option.getId())) {
                merged.getOptions().add(copyOption(option, option.getVoteCount()));
            }
        }
        return merged;
    }

    private Map<Integer, PollOption> byId(Poll poll) {
        Map<Integer, PollOption> result = new HashMap<>();
        for (PollOption option : poll.getOptions()) result.put(option.getId(), option);
        return result;
    }

    private boolean canApplyVoteSnapshot(Poll current, Poll incoming) {
        Map<Integer, PollOption> incomingOptions = byId(incoming);
        for (PollOption option : current.getOptions()) {
            PollOption next = incomingOptions.get(option.getId());
            if (next != null && next.getVoteCount() < option.getVoteCount()) return false;
        }
        return true;
    }

    private boolean hasVoteCountIncrease(Poll current, Poll incoming) {
        Map<Integer, PollOption> currentOptions = byId(current);
        for (PollOption option : incoming.getOptions()) {
            PollOption previous = currentOptions.get(option.getId());
            if (previous == null || option.getVoteCount() > previous.getVoteCount()) return true;
        }
        return false;
    }

    private PollOption copyOption(PollOption option, int voteCount) {
        return new PollOption(
                option.getId(),
                option.getPollId(),
                option.getSlot(),
                option.getLabel(),
                option.getDescription(),
                voteCount
        );
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
