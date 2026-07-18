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

    public synchronized void init() throws SQLException {
        File dbFile = new File(plugin.getDataFolder(), "polls.db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        connection = DriverManager.getConnection(url);
        configureConnection();
        createTables();
        cleanOrphanedRows();
    }

    private void configureConnection() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = 5000");
            st.execute("PRAGMA journal_mode = WAL");
        }
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

    private void cleanOrphanedRows() throws SQLException {
        int deleted;
        try (Statement st = connection.createStatement()) {
            deleted = st.executeUpdate("""
                    DELETE FROM poll_votes
                    WHERE NOT EXISTS (SELECT 1 FROM polls WHERE polls.id = poll_votes.poll_id)
                       OR NOT EXISTS (SELECT 1 FROM poll_options WHERE poll_options.id = poll_votes.option_id)
                    """);
            deleted += st.executeUpdate("""
                    DELETE FROM favorites
                    WHERE NOT EXISTS (SELECT 1 FROM polls WHERE polls.id = favorites.poll_id)
                    """);
            deleted += st.executeUpdate("""
                    DELETE FROM poll_options
                    WHERE NOT EXISTS (SELECT 1 FROM polls WHERE polls.id = poll_options.poll_id)
                    """);
        }
        if (deleted > 0) {
            plugin.getLogger().info(plugin.getLanguageManager().text(
                    "log.orphan_data_cleaned", "count", Integer.toString(deleted)));
        }
    }

    // ─── 写入议题 ───

    public synchronized int insertPoll(UUID creator, String creatorName, String title,
                                       String description, long createdAt, long endsAt) throws SQLException {
        return insertPollInternal(creator, creatorName, title, description, createdAt, endsAt);
    }

    private int insertPollInternal(UUID creator, String creatorName, String title,
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

    public synchronized void insertOption(int pollId, int slot, String label, String description) throws SQLException {
        insertOptionInternal(pollId, slot, label, description);
    }

    private void insertOptionInternal(int pollId, int slot, String label, String description) throws SQLException {
        String sql = "INSERT INTO poll_options (poll_id, slot, label, description) VALUES (?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, pollId);
            ps.setInt(2, slot);
            ps.setString(3, label);
            ps.setString(4, description);
            ps.executeUpdate();
        }
    }

    /**
     * 在同一事务中创建议题及其全部选项，任一写入失败时不会留下不完整议题。
     * 每个选项数组依次为名称和描述。
     */
    public synchronized int insertPollWithOptions(UUID creator, String creatorName, String title,
                                                   String description, long createdAt, long endsAt,
                                                   List<String[]> options) throws SQLException {
        if (options == null || options.size() < 2 || options.size() > 9) {
            throw new IllegalArgumentException("options must contain between 2 and 9 entries");
        }
        for (String[] option : options) {
            if (option == null || option.length < 2) {
                throw new IllegalArgumentException("each option must contain a label and description");
            }
        }

        connection.setAutoCommit(false);
        try {
            int pollId = insertPollInternal(creator, creatorName, title, description, createdAt, endsAt);
            if (pollId < 0) {
                throw new SQLException("创建议题后未返回 ID");
            }
            for (int i = 0; i < options.size(); i++) {
                String[] option = options.get(i);
                insertOptionInternal(pollId, i, option[0], option[1]);
            }
            connection.commit();
            return pollId;
        } catch (SQLException | RuntimeException e) {
            rollback(e);
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    // ─── 读取议题 ───

    public synchronized List<Poll> loadAllPolls() throws SQLException {
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

    public synchronized Poll loadPoll(int id) throws SQLException {
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

    public synchronized boolean hasVoted(int pollId, UUID player) throws SQLException {
        String sql = "SELECT 1 FROM poll_votes WHERE poll_id = ? AND player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, pollId);
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public synchronized int getPlayerVote(int pollId, UUID player) throws SQLException {
        String sql = "SELECT option_id FROM poll_votes WHERE poll_id = ? AND player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, pollId);
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("option_id") : -1;
            }
        }
    }

    /**
     * 原子投票：INSERT OR IGNORE 避免 check-then-act 竞态。
     * 若行已存在（重复投票）返回 false，成功返回 true。
     */
    public synchronized boolean castVote(int pollId, UUID player, int optionId) throws SQLException {
        connection.setAutoCommit(false);
        try {
            long votedAt = System.currentTimeMillis();
            String validate = """
                    SELECT 1
                    FROM polls p
                    JOIN poll_options o ON o.poll_id = p.id
                    WHERE p.id = ? AND p.ends_at > ? AND o.id = ?
            """;
            try (PreparedStatement ps = connection.prepareStatement(validate)) {
                ps.setInt(1, pollId);
                ps.setLong(2, votedAt);
                ps.setInt(3, optionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        connection.rollback();
                        return false;
                    }
                }
            }

            String insert = "INSERT OR IGNORE INTO poll_votes (poll_id, player_uuid, option_id, voted_at) VALUES (?,?,?,?)";
            int inserted;
            try (PreparedStatement ps = connection.prepareStatement(insert)) {
                ps.setInt(1, pollId);
                ps.setString(2, player.toString());
                ps.setInt(3, optionId);
                ps.setLong(4, votedAt);
                inserted = ps.executeUpdate();
            }
            if (inserted == 0) {
                // 主键冲突：该玩家已投过票，静默回滚
                connection.rollback();
                return false;
            }
            String update = "UPDATE poll_options SET vote_count = vote_count + 1 WHERE id = ? AND poll_id = ?";
            try (PreparedStatement ps = connection.prepareStatement(update)) {
                ps.setInt(1, optionId);
                ps.setInt(2, pollId);
                if (ps.executeUpdate() != 1) {
                    throw new SQLException("投票选项不存在或不属于该议题");
                }
            }
            connection.commit();
            return true;
        } catch (SQLException | RuntimeException e) {
            rollback(e);
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void rollback(Throwable cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            cause.addSuppressed(rollbackError);
        }
    }

    // ─── 议题管理 ───

    public synchronized void updatePollTitle(int pollId, String title) throws SQLException {
        String sql = "UPDATE polls SET title = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setInt(2, pollId);
            ps.executeUpdate();
        }
    }

    public synchronized void updatePollDescription(int pollId, String description) throws SQLException {
        String sql = "UPDATE polls SET description = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, description);
            ps.setInt(2, pollId);
            ps.executeUpdate();
        }
    }

    public synchronized void updatePollEndsAt(int pollId, long endsAt) throws SQLException {
        String sql = "UPDATE polls SET ends_at = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, endsAt);
            ps.setInt(2, pollId);
            ps.executeUpdate();
        }
    }

    public synchronized void deletePoll(int pollId) throws SQLException {
        String sql = "DELETE FROM polls WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, pollId);
            ps.executeUpdate();
        }
    }

    // ─── 过期清理 ───

    public synchronized int deleteExpiredPolls(long cutoffTime) throws SQLException {
        String sql = "DELETE FROM polls WHERE ends_at < ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, cutoffTime);
            return ps.executeUpdate();
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().warning(plugin.getLanguageManager().text(
                    "log.database_close_failed", "error", e.getMessage()));
        }
    }
}
