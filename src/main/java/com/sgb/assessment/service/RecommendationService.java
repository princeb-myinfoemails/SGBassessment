package com.sgb.assessment.service;

import com.sgb.assessment.dto.CircleRecommendation;
import com.sgb.assessment.dto.RecommendationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RecommendationService {
    private final JdbcTemplate jdbc;

    @Value("${recommendations.default-limit:10}")
    private int defaultLimit;

    @Value("${recommendations.max-limit:20}")
    private int maxLimit;

    @Value("${recommendations.inactive-days:60}")
    private int inactiveDays;

    private static final double TOPIC_WEIGHT = 0.45;
    private static final double TAG_WEIGHT = 0.10;
    private static final double ACTIVITY_WEIGHT = 0.20;
    private static final double ENGAGEMENT_WEIGHT = 0.10;
    private static final double RECENCY_WEIGHT = 0.10;
    private static final double SIZE_WEIGHT = 0.05;

    public RecommendationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RecommendationResponse recommend(
            Long userId, Integer requestedLimit) {

        int limit = requestedLimit == null
                ? defaultLimit
                : Math.max(1, Math.min(requestedLimit, maxLimit));

        Map<String, Object> user = findUser(userId);

        Set<String> interests = parseSet(
                (String) user.get("TOPIC_INTERESTS")
        );

        List<Map<String, Object>> circles =
                jdbc.queryForList("""
                SELECT c.*,
                    (
                        SELECT COUNT(*)
                        FROM activity a
                        WHERE a.circle_id = c.id
                        AND a.created_at >= ?
                    ) AS actual_posts,
                    (
                        SELECT COALESCE(
                            AVG(a.reaction_count +
                                a.comment_count +
                                a.save_count), 0)
                        FROM activity a
                        WHERE a.circle_id = c.id
                        AND a.created_at >= ?
                    ) AS avg_engagement
                FROM circles c
                WHERE c.access = 'public'
                AND NOT EXISTS (
                    SELECT 1 FROM memberships m
                    WHERE m.user_id = ?
                    AND m.circle_id = c.id
                )
                """,
                        Timestamp.from(
                                Instant.now().minus(Duration.ofDays(30))
                        ),
                        Timestamp.from(
                                Instant.now().minus(Duration.ofDays(30))
                        ),
                        userId
                );

        List<Candidate> candidates = new ArrayList<>();

        for (Map<String, Object> circle : circles) {

            if (!isEligible(circle)) {
                continue;
            }

            candidates.add(score(circle, interests));
        }

        List<CircleRecommendation> recommendations =
                diversify(candidates, limit, interests.isEmpty());

        return new RecommendationResponse(
                userId,
                recommendations.size(),
                interests.isEmpty()
                        ? "COLD_START_DISCOVERY"
                        : "PERSONALIZED_TOPIC_AND_ACTIVITY",
                recommendations
        );
    }

    private Map<String, Object> findUser(Long userId) {

        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT * FROM users WHERE id = ?", userId
        );

        if (users.isEmpty()) {
            throw new ResponseStatusException(
                    NOT_FOUND, "Member not found: " + userId
            );
        }

        return users.get(0);
    }

    private boolean isEligible(Map<String, Object> c) {

        int memberCount = number(c.get("MEMBER_COUNT"));
        Object maxMembers = c.get("MAX_MEMBERS");

        // Full Circle: do not recommend.
        if (maxMembers != null &&
                memberCount >= number(maxMembers)) {
            return false;
        }

        Timestamp lastActivity =
                (Timestamp) c.get("LAST_ACTIVITY_AT");

        // Unknown activity is treated conservatively.
        if (lastActivity == null) {
            return false;
        }

        long inactiveFor = Duration.between(
                lastActivity.toInstant(),
                Instant.now()
        ).toDays();

        return inactiveFor <= inactiveDays;
    }

    private Candidate score(
            Map<String, Object> c,
            Set<String> interests) {

        String topic = (String) c.get("TOPIC");

        Set<String> tags = parseSet(
                (String) c.get("TAGS")
        );

        // 1. Topic relevance
        double topicScore = 0;

        if (topic != null && interests.contains(topic)) {
            topicScore = 100;
        }

        // 2. Related tag relevance
        long matchingTags = tags.stream()
                .filter(interests::contains)
                .count();

        double tagScore = tags.isEmpty()
                ? 0
                : Math.min(
                100,
                (matchingTags * 100.0) /
                        Math.max(1, interests.size())
        );

        // 3. Recent activity volume
        int posts = number(c.get("ACTUAL_POSTS"));
        int chats = number(c.get("CHAT_MESSAGES_30D"));

        double activityScore = normalize(
                Math.log1p(posts * 2.0 + chats * 0.15),
                Math.log1p(100)
        );

        // 4. Engagement
        double engagement =
                ((Number) c.get("AVG_ENGAGEMENT")).doubleValue();

        double engagementScore = normalize(
                Math.log1p(engagement),
                Math.log1p(30)
        );

        // 5. Recency
        Timestamp lastActivity =
                (Timestamp) c.get("LAST_ACTIVITY_AT");

        long days = Math.max(
                0,
                Duration.between(
                        lastActivity.toInstant(),
                        Instant.now()
                ).toDays()
        );

        double recencyScore = Math.max(
                0, 100 - (days * 100.0 / inactiveDays)
        );

        // 6. Community size suitability
        int members = number(c.get("MEMBER_COUNT"));

        double sizeScore;

        if (members <= 10) {
            sizeScore = 100;
        } else if (members <= 50) {
            sizeScore = 90;
        } else if (members <= 100) {
            sizeScore = 75;
        } else {
            sizeScore = 60;
        }

        // No topic interests = discovery mode.
        // Do not pretend that profile data reveals interests.
        double finalScore;

        if (interests.isEmpty()) {
            finalScore =
                    0.55 * activityScore +
                            0.20 * engagementScore +
                            0.20 * recencyScore +
                            0.05 * sizeScore;
        } else {
            finalScore =
                    TOPIC_WEIGHT * topicScore +
                            TAG_WEIGHT * tagScore +
                            ACTIVITY_WEIGHT * activityScore +
                            ENGAGEMENT_WEIGHT * engagementScore +
                            RECENCY_WEIGHT * recencyScore +
                            SIZE_WEIGHT * sizeScore;
        }

        return new Candidate(
                c,
                round(finalScore),
                topicScore,
                tagScore,
                activityScore,
                engagementScore,
                recencyScore
        );
    }

    private double normalize(double value, double max) {
        if (max <= 0) return 0;
        return Math.min(100, value / max * 100);
    }

    private List<CircleRecommendation> diversify(
            List<Candidate> candidates,
            int limit,
            boolean coldStart) {

        List<Candidate> remaining =
                new ArrayList<>(candidates);

        List<CircleRecommendation> results =
                new ArrayList<>();

        Map<String, Integer> selectedTopics =
                new HashMap<>();

        while (!remaining.isEmpty() && results.size() < limit) {

            Candidate best = null;
            double bestAdjustedScore = -1;

            for (Candidate candidate : remaining) {

                String topic = candidate.topic();

                int occurrences = selectedTopics.getOrDefault(
                        topic == null ? "general" : topic, 0
                );

                double penalty = occurrences == 0
                        ? 0
                        : occurrences == 1 ? 25 : 40;

                double adjustedScore = Math.max(
                        0, candidate.score() - penalty
                );

                if (adjustedScore > bestAdjustedScore) {
                    bestAdjustedScore = adjustedScore;
                    best = candidate;
                }
            }

            if (best == null) break;

            remaining.remove(best);

            String topic = best.topic() == null
                    ? "general"
                    : best.topic();

            selectedTopics.merge(topic, 1, Integer::sum);

            results.add(toRecommendation(
                    best, bestAdjustedScore, coldStart
            ));
        }

        return results;
    }

    private CircleRecommendation toRecommendation(
            Candidate candidate,
            double score,
            boolean coldStart) {

        Map<String, Object> c = candidate.data();

        List<String> reasons = new ArrayList<>();

        String topic = candidate.topic();

        if (!coldStart &&
                candidate.topicScore() == 100) {

            reasons.add(
                    "Matches your interest in " +
                            topic.replace("-", " ") + "."
            );

        } else if (!coldStart &&
                candidate.tagScore() > 0) {

            reasons.add(
                    "Its topics overlap with interests you selected."
            );
        }

        if (candidate.activityScore() >= 40) {
            reasons.add(
                    "Members have been actively posting and chatting recently."
            );
        }

        if (candidate.engagementScore() >= 35) {
            reasons.add(
                    "Recent discussions are receiving member engagement."
            );
        }

        if (coldStart) {
            reasons.add(
                    "A place to discover new perspectives and connect with other women."
            );
        }

        if (reasons.isEmpty()) {
            reasons.add(
                    "An active community where you can explore and connect."
            );
        }

        return new CircleRecommendation(
                ((Number) c.get("ID")).longValue(),
                (String) c.get("NAME"),
                (String) c.get("DESCRIPTION"),
                topic,
                (String) c.get("FORMAT"),
                number(c.get("MEMBER_COUNT")),
                (String) c.get("JOIN_POLICY"),
                round(score),
                reasons
        );
    }

    private Set<String> parseSet(String value) {

        if (value == null || value.isBlank()) {
            return Collections.emptySet();
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private int number(Object value) {
        return value == null
                ? 0
                : ((Number) value).intValue();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record Candidate(
            Map<String, Object> data,
            double score,
            double topicScore,
            double tagScore,
            double activityScore,
            double engagementScore,
            double recencyScore
    ) {
        String topic() {
            return (String) data.get("TOPIC");
        }
    }
}
