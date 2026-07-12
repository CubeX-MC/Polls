package com.polls.model;

public class PollOption {

    private final int id;
    private final int pollId;
    private final int slot; // 0-based display order
    private String label;
    private String description; // lore text, may be empty
    private int voteCount;

    public PollOption(int id, int pollId, int slot, String label, String description, int voteCount) {
        this.id = id;
        this.pollId = pollId;
        this.slot = slot;
        this.label = label;
        this.description = description;
        this.voteCount = voteCount;
    }

    public int getId() { return id; }
    public int getPollId() { return pollId; }
    public int getSlot() { return slot; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getVoteCount() { return voteCount; }
    public void setVoteCount(int voteCount) { this.voteCount = voteCount; }
    public void incrementVoteCount() { this.voteCount++; }
}
