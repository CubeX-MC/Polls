package com.polls.db;

import com.polls.model.Poll;
import com.polls.model.PollOption;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
