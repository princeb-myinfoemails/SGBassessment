package com.sgb.assessment.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;

@Component
public class DataInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public DataInitializer(
            JdbcTemplate jdbc,
            ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void run(String... args) throws Exception {

        // Files are expected in ./data, at project root.
        loadTopics();
        loadUsers();
        loadCircles();
        loadMemberships();
        loadActivity();
    }

    private JsonNode read(String file) throws Exception {
        return mapper.readTree(
                new File("data/" + file + ".json")
        );
    }

    private Timestamp timestamp(JsonNode node) {
        if (node == null || node.isNull()) return null;

        String value = node.asText();

        try {
            return Timestamp.from(Instant.parse(value));
        } catch (Exception ignored) {
            return Timestamp.from(
                    OffsetDateTime.parse(value).toInstant()
            );
        }
    }

    private String csv(JsonNode node) {
        if (node == null || !node.isArray()) return "";

        StringBuilder result = new StringBuilder();

        for (JsonNode item : node) {
            if (!result.isEmpty()) result.append(",");
            result.append(item.asText().replace(",", ""));
        }

        return result.toString();
    }

    private void loadTopics() throws Exception {
        for (JsonNode n : read("topics")) {
            jdbc.update("""
                INSERT INTO topics
                (id, slug, label, domain)
                VALUES (?, ?, ?, ?)
                """,
                    n.get("id").asLong(),
                    n.get("slug").asText(),
                    n.get("label").asText(),
                    n.path("domain").asText(null)
            );
        }
    }

    private void loadUsers() throws Exception {
        for (JsonNode n : read("users")) {
            jdbc.update("""
                INSERT INTO users
                (id, first_name, last_name, has_photo,
                 bio, job_title, company, industry, location,
                 joined_at, topic_interests, profile_completeness)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                    n.get("id").asLong(),
                    n.path("first_name").asText(null),
                    n.path("last_name").asText(null),
                    n.path("has_photo").asBoolean(),
                    n.path("bio").asText(null),
                    n.path("job_title").asText(null),
                    n.path("company").asText(null),
                    n.path("industry").asText(null),
                    n.path("location").asText(null),
                    timestamp(n.get("joined_at")),
                    csv(n.get("topic_interests")),
                    n.path("profile_completeness").asDouble(0)
            );
        }
    }

    private void loadCircles() throws Exception {
        for (JsonNode n : read("circles")) {
            jdbc.update("""
                INSERT INTO circles
                (id, name, slug, description, topic, tags,
                 cover_image_url, format, access, join_policy,
                 max_members, member_count, created_at,
                 last_activity_at, feed_posts_30d, chat_messages_30d)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                    n.get("id").asLong(),
                    n.path("name").asText(),
                    n.path("slug").asText(null),
                    n.path("description").asText(null),
                    n.path("topic").asText(null),
                    csv(n.get("tags")),
                    n.path("cover_image_url").asText(null),
                    n.path("format").asText(null),
                    n.path("access").asText(null),
                    n.path("join_policy").asText(null),
                    n.path("max_members").isNull()
                            ? null : n.path("max_members").asInt(),
                    n.path("member_count").asInt(0),
                    timestamp(n.get("created_at")),
                    timestamp(n.get("last_activity_at")),
                    n.path("feed_posts_30d").asInt(0),
                    n.path("chat_messages_30d").asInt(0)
            );
        }
    }

    private void loadMemberships() throws Exception {
        for (JsonNode n : read("memberships")) {
            jdbc.update("""
                INSERT INTO memberships
                (user_id, circle_id, role, joined_at)
                VALUES (?, ?, ?, ?)
                """,
                    n.get("user_id").asLong(),
                    n.get("circle_id").asLong(),
                    n.path("role").asText("member"),
                    timestamp(n.get("joined_at"))
            );
        }
    }

    private void loadActivity() throws Exception {
        for (JsonNode n : read("activity")) {
            jdbc.update("""
                INSERT INTO activity
                (id, circle_id, author_user_id, body,
                 reaction_count, comment_count, save_count, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                    n.get("id").asLong(),
                    n.get("circle_id").asLong(),
                    n.get("author_user_id").asLong(),
                    n.path("body").asText(null),
                    n.path("reaction_count").asInt(0),
                    n.path("comment_count").asInt(0),
                    n.path("save_count").asInt(0),
                    timestamp(n.get("created_at"))
            );
        }
    }
}
