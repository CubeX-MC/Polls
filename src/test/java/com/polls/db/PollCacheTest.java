package com.polls.db;

import com.polls.model.Poll;
import com.polls.model.PollOption;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PollCacheTest {

    @Test
    void voteSnapshotsNeverMoveCountsBackwards() {
        PollCache cache = new PollCache(null);
        cache.addPoll(pollWithVotes(1));

        AtomicInteger changes = new AtomicInteger();
        Runnable removeListener = cache.addChangeListener(changes::incrementAndGet);

        cache.updateVoteSnapshot(pollWithVotes(3));
        cache.updateVoteSnapshot(pollWithVotes(2));

        assertEquals(3, totalVotes(cache.getById(1)));
        assertEquals(1, changes.get());

        removeListener.run();
        cache.updateVoteSnapshot(pollWithVotes(4));
        assertEquals(4, totalVotes(cache.getById(1)));
        assertEquals(1, changes.get());
    }

    @Test
    void addPollUpsertsByIdInsteadOfCreatingDuplicates() {
        PollCache cache = new PollCache(null);
        cache.addPoll(pollWithVotes(1));

        Poll replacement = pollWithVotes(2);
        replacement.setTitle("Updated question");
        cache.addPoll(replacement);

        assertEquals(1, cache.getAll().size());
        assertEquals("Updated question", cache.getById(1).getTitle());
        assertEquals(2, totalVotes(cache.getById(1)));
    }

    @Test
    void voteSnapshotsRejectInconsistentPerOptionCounts() {
        PollCache cache = new PollCache(null);
        Poll current = pollWithVotes(3);
        current.getOptions().get(1).setVoteCount(2);
        current.setTitle("Current question");
        cache.addPoll(current);

        Poll stale = pollWithVotes(2);
        stale.getOptions().get(1).setVoteCount(3);
        stale.setTitle("Stale question");
        cache.updateVoteSnapshot(stale);

        Poll result = cache.getById(1);
        assertEquals("Current question", result.getTitle());
        assertEquals(3, result.getOptions().get(0).getVoteCount());
        assertEquals(2, result.getOptions().get(1).getVoteCount());
        assertEquals(5, totalVotes(result));
    }

    @Test
    void managementUpdatesKeepNewerVoteCounts() {
        PollCache cache = new PollCache(null);
        cache.addPoll(pollWithVotes(4));

        Poll stale = pollWithVotes(2);
        stale.setTitle("Edited question");
        cache.updatePoll(stale);

        Poll result = cache.getById(1);
        assertEquals("Edited question", result.getTitle());
        assertEquals(4, totalVotes(result));
    }

    @Test
    void managementFieldUpdatesDoNotOverwriteEachOther() {
        PollCache cache = new PollCache(null);
        cache.addPoll(pollWithVotes(4));

        cache.updatePollTitle(1, "Updated title");
        cache.updatePollDescription(1, "Updated description");
        cache.updatePollEndsAt(1, 12345L);

        Poll result = cache.getById(1);
        assertEquals("Updated title", result.getTitle());
        assertEquals("Updated description", result.getDescription());
        assertEquals(12345L, result.getEndsAt());
        assertEquals(4, totalVotes(result));
    }

    @Test
    void deletedPollRejectsLateSnapshotsAndDuplicateAdds() {
        PollCache cache = new PollCache(null);
        cache.addPoll(pollWithVotes(1));

        cache.removePoll(1);
        cache.updateVoteSnapshot(pollWithVotes(2));
        cache.addPoll(pollWithVotes(3));

        assertNull(cache.getById(1));
        assertEquals(0, cache.getAll().size());
    }

    private Poll pollWithVotes(int voteCount) {
        Poll poll = new Poll(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Player",
                "Question",
                "",
                1L,
                Long.MAX_VALUE
        );
        poll.getOptions().add(new PollOption(10, 1, 0, "Yes", "", voteCount));
        poll.getOptions().add(new PollOption(11, 1, 1, "No", "", 0));
        return poll;
    }

    private int totalVotes(Poll poll) {
        return poll.getOptions().stream().mapToInt(PollOption::getVoteCount).sum();
    }
}
