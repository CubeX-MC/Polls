package com.polls.db;

import com.polls.PollsPlugin;
import com.polls.model.Poll;
import com.polls.model.PollOption;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Database {

    private final PollsPlugin plugin;
    private Connection connection;

    public Database(PollsPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() throws SQLException {
        File dbFile = new File(plugin.getDataFolder(), "polls.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        connection = DriverManager.getConnection(url);
        createTables();
    }

    private void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS polls (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    creator     TEXT    NOT NULL,
                    creator_name TEXT   NOT NULL,
                    title       TEXT    NOT NULL,
                    description TEXT    NOT NULL DEFAULT '',
                    created_at  INTEGER NOT NULL,
                    ends_at     INTEGER NOT NULL
                )
            """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS poll_options (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    poll_id     INTEGER NOT NULL,
                    slot        INTEGER NOT NULL,
                    label       TEXT    NOT NULL,
                    description TEXT    NOT NULL DEFAULT '',
                    vote_count  INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (poll_id) REFERENCES polls(id) ON DELETE CASCADE
                )
            """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS poll_votes (
                    poll_id     INTEGER NOT NULL,
                    player_uuid TEXT    NOT NULL,
                    option_id   INTEGER NOT NULL,
                    voted_at    INTEGER NOT NULL,
                    PRIMARY KEY (poll_id, player_uuid),
                    FOREIGN KEY (poll_id) REFERENCES polls(id) ON DELETE CASCADE,
                    FOREIGN KEY (option_id) REFERENCES poll_options(id) ON DELETE CASCADE
                )
            """);
            // 预留收藏表
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS favorites (
                    player_uuid TEXT    NOT NULL,
                    poll_id     INTEGER NOT NULL,
                    PRIMARY KEY (player_uuid, poll_id),
                    FOREIGN KEY (poll_id) REFERENCES polls(id) ON DELETE CASCADE
                )
            """);
            // 性能索引
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_poll_votes_player ON poll_votes(player_uuid)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_polls_ends_at ON polls(ends_at)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_poll_options_poll_id ON poll_options(poll_id)");
        }
    }

    // ─── 写入议题 ───

    public int insertPoll(UUID creator, String creatorName, String title,
                          String description, long createdAt, long endsAt) throws SQLException {
        String sql = "INSERT INTO polls (creator, creator_name, title, description, created_at, ends_at) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, creator.toString());
            ps.setString(2, creatorName);
            ps.setString(3, title);
            ps.setString(4, description);
            ps.setLong(5, createdAt);
            ps.setLong(6, endsAt);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    public void insertOption(int pollId, int slot, String label, String description) throws SQLException {
        String sql = "INSERT INTO poll_options (poll_id, slot, label, description) VALUES (?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, pollId);
            ps.setInt(2, slot);
            ps.setString(3, label);
            ps.setString(4, description);
            ps.executeUpdate();
        }
    }

    // ─── 读取议题 ───

    public List<Poll> loadAllPolls() throws SQLException {
        List<Poll> polls = new ArrayList<>();
        String sql = "SELECT * FROM polls ORDER BY ends_at DESC";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                polls.add(mapPoll(rs));
            }
        }
        if (!polls.isEmpty()) {
            loadAllOptions(polls);
        }
        return polls;
    }

    private void loadAllOptions(List<Poll> polls) throws SQLException {
        String sql = "SELECT * FROM poll_options ORDER BY poll_id, slot";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            Map<Integer, Poll> pollMap = new LinkedHashMap<>();
            for (Poll p : polls) pollMap.put(p.getId(), p);

            while (rs.next()) {
                int pollId = rs.getInt("poll_id");
                Poll poll = pollMap.get(pollId);
                if (poll != null) {
                    poll.getOptions().add(new PollOption(
                            rs.getInt("id"),
                            pollId,
                            rs.getInt("slot"),
                            rs.getString("label"),
                            rs.getString("description"),
                            rs.getInt("vote_count")
                    ));
                }
            }
        }
    }

    public Poll loadPoll(int id) throws SQLException {
        String sql = "SELECT * FROM polls WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Poll poll = mapPoll(rs);
                loadOptions(poll);
                return poll;
            }
        }
    }

    private void loadOptions(Poll poll) throws SQLException {
        String sql = "SELECT * FROM poll_options WHERE poll_id = ? ORDER BY slot";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, poll.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    poll.getOptions().add(new PollOption(
                            rs.getInt("id"),
                            rs.getInt("poll_id"),
                            rs.getInt("slot"),
                            rs.getString("label"),
                            rs.getString("description"),
                            rs.getInt("vote_count")
                    ));
                }
            }
        }
    }

    private Poll mapPoll(ResultSet rs) throws SQLException {
        return new Poll(
                rs.getInt("id"),
                UUID.fromString(rs.getString("creator")),
                rs.getString("creator_name"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getLong("created_at"),
                rs.getLong("ends_at")
        );
    }

    // ─── 投票 ───

    public boolean hasVoted(int pollId, UUID player) throws SQLException {
        String sql = "SELECT 1 FROM poll_votes WHERE poll_id = ? AND player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, pollId);
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public int getPlayerVote(int pollId, UUID player) throws SQLException {
        String sql = "SELECT option_id FROM poll_votes WHERE poll_id = ? AND player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, pollId);
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("option_id") : -1;
            }
        }
    }

    public void castVote(int pollId, UUID player, int optionId) throws SQLException {
        connection.setAutoCommit(false);
        try {
            String insert = "INSERT INTO poll_votes (poll_id, player_uuid, option_id, voted_at) VALUES (?,?,?,?)";
            try (PreparedStatement ps = connection.prepareStatement(insert)) {
                ps.setInt(1, pollId);
                ps.setString(2, player.toString());
                ps.setInt(3, optionId);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            }
            String update = "UPDATE poll_options SET vote_count = vote_count + 1 WHERE id = ?";
            try (PreparedStatement ps = connection.prepareStatement(update)) {
                ps.setInt(1, optionId);
                ps.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    // ─── 议题管理 ───

    public void updatePollTitleDesc(int pollId, String title, String description) throws SQLException {
        String sql = "UPDATE polls SET title = ?, description = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, description);
            ps.setInt(3, pollId);
            ps.executeUpdate();
        }
    }

    public void updatePollEndsAt(int pollId, long endsAt) throws SQLException {
        String sql = "UPDATE polls SET ends_at = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, endsAt);
            ps.setInt(2, pollId);
            ps.executeUpdate();
        }
    }

    public void deletePoll(int pollId) throws SQLException {
        String sql = "DELETE FROM polls WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, pollId);
            ps.executeUpdate();
        }
    }

    // ─── 过期清理 ───

    public int deleteExpiredPolls(long cutoffTime) throws SQLException {
        String sql = "DELETE FROM polls WHERE ends_at < ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, cutoffTime);
            return ps.executeUpdate();
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("关闭数据库连接失败: " + e.getMessage());
        }
    }
}
