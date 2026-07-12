package com.polls.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Poll {

    public enum Status { ACTIVE, ENDED }

    private final int id;
    private final UUID creator;
    private final String creatorName;
    private String title;
    private String description;
    private final long createdAt;
    private long endsAt;
    private final List<PollOption> options = new ArrayList<>();

    public Poll(int id, UUID creator, String creatorName, String title,
                String description, long createdAt, long endsAt) {
        this.id = id;
        this.creator = creator;
        this.creatorName = creatorName;
        this.title = title;
        this.description = description;
        this.createdAt = createdAt;
        this.endsAt = endsAt;
    }

    public Status getStatus() {
        return System.currentTimeMillis() >= endsAt ? Status.ENDED : Status.ACTIVE;
    }

    public boolean isActive() { return getStatus() == Status.ACTIVE; }

    public long getRemainingMillis() { return Math.max(0, endsAt - System.currentTimeMillis()); }

    // Getters / setters
    public int getId() { return id; }
    public UUID getCreator() { return creator; }
    public String getCreatorName() { return creatorName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public long getCreatedAt() { return createdAt; }
    public long getEndsAt() { return endsAt; }
    public void setEndsAt(long endsAt) { this.endsAt = endsAt; }
    public List<PollOption> getOptions() { return options; }
}
