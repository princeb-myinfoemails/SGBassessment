
CREATE TABLE IF NOT EXISTS topics (
    id BIGINT PRIMARY KEY,
    slug VARCHAR(100) NOT NULL UNIQUE,
    label VARCHAR(150) NOT NULL,
    domain VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    has_photo BOOLEAN,
    bio CLOB,
    job_title VARCHAR(200),
    company VARCHAR(200),
    industry VARCHAR(150),
    location VARCHAR(200),
    joined_at TIMESTAMP,
    topic_interests CLOB,
    profile_completeness DOUBLE
);

CREATE TABLE IF NOT EXISTS circles (
    id BIGINT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(200),
    description CLOB,
    topic VARCHAR(100),
    tags CLOB,
    cover_image_url VARCHAR(1000),
    format VARCHAR(30),
    access VARCHAR(30),
    join_policy VARCHAR(30),
    max_members INT,
    member_count INT DEFAULT 0,
    created_at TIMESTAMP,
    last_activity_at TIMESTAMP,
    feed_posts_30d INT DEFAULT 0,
    chat_messages_30d INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS memberships (
    user_id BIGINT NOT NULL,
    circle_id BIGINT NOT NULL,
    role VARCHAR(30),
    joined_at TIMESTAMP,
    PRIMARY KEY (user_id, circle_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (circle_id) REFERENCES circles(id)
);

CREATE TABLE IF NOT EXISTS activity (
    id BIGINT PRIMARY KEY,
    circle_id BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    body CLOB,
    reaction_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    save_count INT DEFAULT 0,
    created_at TIMESTAMP,
    FOREIGN KEY (circle_id) REFERENCES circles(id),
    FOREIGN KEY (author_user_id) REFERENCES users(id)
);

CREATE INDEX idx_memberships_user
    ON memberships(user_id);

CREATE INDEX idx_memberships_circle
    ON memberships(circle_id);

CREATE INDEX idx_circles_topic
    ON circles(topic);

CREATE INDEX idx_circles_activity
    ON circles(last_activity_at);

CREATE INDEX idx_activity_circle_date
    ON activity(circle_id, created_at);
