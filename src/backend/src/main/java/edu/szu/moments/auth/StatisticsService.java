package edu.szu.moments.auth;

import edu.szu.moments.api.ApiFailure;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
class StatisticsService {
    record Totals(long userCount, long postCount, long commentCount, long ratingCount) {}
    record Day(LocalDate date, long newUsers, long newPosts, Long activeUsers) {}
    record Statistics(String timezone, Instant trackingStartedAt, Totals totals, List<Day> daily) {}
    private final JdbcTemplate db;
    StatisticsService(JdbcTemplate db) { this.db=db; }

    @Transactional(readOnly=true)
    public Statistics get(String rawFrom, String rawTo) {
        LocalDate today = LocalDate.now(ActivityRecorder.ZONE);
        LocalDate to = parse(rawTo,today), from = parse(rawFrom,to.minusDays(29));
        if (from.isAfter(to) || to.isAfter(today) || ChronoUnit.DAYS.between(from,to)>365)
            throw invalid();
        Instant started = db.queryForObject("SELECT started_at FROM activity_tracking WHERE id=1",
                (rs,n)->rs.getTimestamp(1,utc()).toInstant());
        var totals = new Totals(count("SELECT COUNT(*) FROM users WHERE role='USER'"),
                count("SELECT COUNT(*) FROM posts p JOIN users u ON u.id=p.author_id WHERE p.status='ACTIVE' AND u.status='ACTIVE'"),
                count("SELECT COUNT(*) FROM comments c JOIN posts p ON p.id=c.post_id JOIN users a ON a.id=p.author_id "
                        + "JOIN users u ON u.id=c.user_id WHERE c.status='ACTIVE' AND p.status='ACTIVE' AND a.status='ACTIVE' AND u.status='ACTIVE'"),
                count("SELECT COUNT(*) FROM ratings r JOIN posts p ON p.id=r.post_id JOIN users a ON a.id=p.author_id "
                        + "JOIN users u ON u.id=r.user_id WHERE p.status='ACTIVE' AND a.status='ACTIVE' AND u.status='ACTIVE'"));
        Map<LocalDate,Long> newUsers = creationDays("users",from,to), newPosts = creationDays("posts",from,to);
        var active = new HashMap<LocalDate,Long>();
        db.query("SELECT activity_day,COUNT(*) amount FROM daily_user_activity WHERE activity_day BETWEEN ? AND ? GROUP BY activity_day",
                (org.springframework.jdbc.core.RowCallbackHandler)rs -> active.put(rs.getDate(1).toLocalDate(),rs.getLong(2)),
                java.sql.Date.valueOf(from),java.sql.Date.valueOf(to));
        var days = new ArrayList<Day>();
        LocalDate firstDay = started.atZone(ActivityRecorder.ZONE).toLocalDate();
        for (LocalDate day=from; !day.isAfter(to); day=day.plusDays(1))
            days.add(new Day(day,newUsers.getOrDefault(day,0L),newPosts.getOrDefault(day,0L),
                    day.isBefore(firstDay) ? null : active.getOrDefault(day,0L)));
        return new Statistics("Asia/Shanghai",started,totals,days);
    }

    private Map<LocalDate,Long> creationDays(String table, LocalDate from, LocalDate to) {
        // Table is an internal constant, never derived from request parameters. Range keeps created_at index usable.
        var result = new HashMap<LocalDate,Long>();
        String sql = "SELECT DATE(DATE_ADD(created_at,INTERVAL 8 HOUR)) day,COUNT(*) amount FROM "+table
                + " WHERE created_at>=? AND created_at<?"+(table.equals("users") ? " AND role='USER'" : "")+" GROUP BY day";
        db.query(connection -> {
            var ps=connection.prepareStatement(sql);
            ps.setTimestamp(1,Timestamp.from(from.atStartOfDay(ActivityRecorder.ZONE).toInstant()),utc());
            ps.setTimestamp(2,Timestamp.from(to.plusDays(1).atStartOfDay(ActivityRecorder.ZONE).toInstant()),utc());
            return ps;
        },(org.springframework.jdbc.core.RowCallbackHandler)rs->result.put(rs.getDate(1).toLocalDate(),rs.getLong(2)));
        return result;
    }
    private long count(String sql) { return db.queryForObject(sql,Long.class); }
    private static Calendar utc() { return Calendar.getInstance(TimeZone.getTimeZone("UTC")); }
    private static LocalDate parse(String raw, LocalDate fallback) {
        if (raw==null) return fallback;
        if (!raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid();
        try { return LocalDate.parse(raw); } catch (DateTimeParseException e) { throw invalid(); }
    }
    private static ApiFailure invalid() {
        return new ApiFailure(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","日期须为 YYYY-MM-DD，起日不晚于止日，最多 366 天且不得晚于今天");
    }
}
