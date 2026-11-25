-- Axoo application schema for a fresh PostgreSQL database.
-- Populate cities and universities for your own environment.

BEGIN;
SET LOCAL search_path TO public;

CREATE TYPE vote_type AS ENUM ('upvote', 'downvote');

CREATE TYPE report_target_type AS ENUM ('post', 'comment', 'user');

CREATE TABLE cities (
    id uuid PRIMARY KEY,
    name text NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT NOW()
);

CREATE TABLE universities (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL UNIQUE,
    domain text NOT NULL UNIQUE,
    city_id uuid NOT NULL REFERENCES cities(id),
    created_at timestamptz NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX universities_domain_lower_unique
    ON universities (LOWER(domain));

CREATE TABLE users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email_hash text NOT NULL UNIQUE,
    nickname text NOT NULL UNIQUE,
    university_id uuid NOT NULL REFERENCES universities(id),
    avatar_config jsonb,
    bio text NOT NULL DEFAULT '' CHECK (char_length(bio) <= 200),
    bio_entities jsonb NOT NULL DEFAULT '[]'::jsonb,
    is_profile_public boolean NOT NULL DEFAULT FALSE,
    terms_version text NOT NULL DEFAULT '',
    terms_accepted_at timestamptz,
    suspended_until timestamptz,
    suspension_reason text NOT NULL DEFAULT '',
    is_banned boolean NOT NULL DEFAULT FALSE,
    is_alpha_tester boolean NOT NULL DEFAULT FALSE,
    is_moderator boolean NOT NULL DEFAULT FALSE,
    is_admin boolean NOT NULL DEFAULT FALSE,
    created_at timestamptz NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_users_nickname_lower_unique
    ON users (LOWER(nickname));

CREATE TABLE posts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id),
    city_id uuid NOT NULL REFERENCES cities(id),
    body text NOT NULL,
    flair text,
    poll_options jsonb,
    media jsonb,
    entities jsonb NOT NULL DEFAULT '[]'::jsonb,
    client_id uuid,
    is_hidden boolean NOT NULL DEFAULT FALSE,
    is_deleted boolean NOT NULL DEFAULT FALSE,
    created_at timestamptz NOT NULL DEFAULT NOW(),
    updated_at timestamptz NOT NULL DEFAULT NOW(),
    CONSTRAINT posts_flair_check CHECK (
        flair IS NULL OR flair IN (
            'Meme', 'Spotted', 'Esami', 'Confessioni',
            'Dell''Andro', 'Fraccacreta', 'Party'
        )
    ),
    CONSTRAINT posts_poll_options_valid CHECK (
        poll_options IS NULL OR (
            jsonb_typeof(poll_options) = 'array' AND jsonb_array_length(poll_options) BETWEEN 2 AND 4
        )
    ),
    CONSTRAINT posts_media_object_check CHECK (
        media IS NULL OR jsonb_typeof(media) = 'object'
    )
);

CREATE INDEX idx_posts_city_feed_new
    ON posts (city_id, created_at DESC, id DESC)
    WHERE is_deleted = FALSE AND is_hidden = FALSE;
CREATE INDEX idx_posts_user ON posts (user_id);
CREATE UNIQUE INDEX idx_posts_user_client_id
    ON posts (user_id, client_id) WHERE client_id IS NOT NULL;

CREATE TABLE post_votes (
    post_id uuid NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type vote_type NOT NULL,
    created_at timestamptz NOT NULL DEFAULT NOW(),
    PRIMARY KEY (post_id, user_id)
);

CREATE TABLE comments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id uuid NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_comment_id uuid REFERENCES comments(id) ON DELETE SET NULL,
    body text NOT NULL,
    media jsonb,
    entities jsonb NOT NULL DEFAULT '[]'::jsonb,
    client_id uuid,
    is_hidden boolean NOT NULL DEFAULT FALSE,
    is_deleted boolean NOT NULL DEFAULT FALSE,
    created_at timestamptz NOT NULL DEFAULT NOW(),
    updated_at timestamptz NOT NULL DEFAULT NOW(),
    CONSTRAINT comments_parent_not_self CHECK (parent_comment_id IS NULL OR parent_comment_id <> id)
);

CREATE INDEX idx_comments_post ON comments (post_id);
CREATE INDEX idx_comments_post_created
    ON comments (post_id, created_at DESC, id DESC) WHERE is_deleted = FALSE;
CREATE INDEX idx_comments_user_created_at
    ON comments (user_id, created_at DESC);
CREATE UNIQUE INDEX idx_comments_user_client_id
    ON comments (user_id, client_id) WHERE client_id IS NOT NULL;

CREATE INDEX idx_comments_parent ON comments (parent_comment_id) WHERE parent_comment_id IS NOT NULL;

CREATE TABLE comment_votes (
    comment_id uuid NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type vote_type NOT NULL,
    created_at timestamptz NOT NULL DEFAULT NOW(),
    PRIMARY KEY (comment_id, user_id)
);

CREATE TABLE moderation_actions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    moderator_id uuid REFERENCES users(id) ON DELETE SET NULL,
    action text NOT NULL,
    target_type text NOT NULL,
    target_id uuid NOT NULL,
    reason text NOT NULL DEFAULT '',
    duration_hours integer,
    author_seen_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT NOW(),
    CONSTRAINT moderation_actions_action_check CHECK (
        action IN ('hide', 'keep', 'restore', 'suspend', 'ban', 'unsuspend')
    ),
    CONSTRAINT moderation_actions_target_type_check CHECK (
        target_type IN ('post', 'comment', 'user')
    )
);

CREATE INDEX idx_moderation_actions_created_at
    ON moderation_actions (created_at DESC);
CREATE INDEX idx_moderation_actions_pending_author_notice
    ON moderation_actions (created_at DESC)
    WHERE action = 'hide' AND author_seen_at IS NULL;

CREATE TABLE reports (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_type report_target_type NOT NULL,
    target_id uuid NOT NULL,
    reason text NOT NULL,
    moderation_action_id uuid REFERENCES moderation_actions(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_reports_reporter_target
    ON reports (reporter_id, target_type, target_id);
CREATE INDEX idx_reports_open_target
    ON reports (target_type, target_id, created_at DESC)
    WHERE moderation_action_id IS NULL;
CREATE INDEX idx_reports_action
    ON reports (moderation_action_id)
    WHERE moderation_action_id IS NOT NULL;

CREATE TABLE notifications (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    recipient_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    actor_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id uuid NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    comment_id uuid REFERENCES comments(id) ON DELETE CASCADE,
    kind text NOT NULL,
    milestone integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    seen_at timestamptz,
    UNIQUE (recipient_id, comment_id),
    CHECK (
        (kind IN ('post_comment', 'comment_reply') AND comment_id IS NOT NULL AND milestone = 0)
        OR (kind = 'post_appreciation' AND comment_id IS NULL AND milestone IN (10, 25))
        OR (kind = 'comment_appreciation' AND comment_id IS NOT NULL AND milestone = 5)
    )
);

CREATE UNIQUE INDEX notifications_post_appreciation_idx
    ON notifications (recipient_id, post_id, milestone) WHERE kind = 'post_appreciation';
CREATE UNIQUE INDEX notifications_comment_appreciation_idx
    ON notifications (recipient_id, comment_id, milestone) WHERE kind = 'comment_appreciation';

CREATE INDEX notifications_recipient_id_idx ON notifications (recipient_id, id DESC);
CREATE INDEX notifications_unseen_idx ON notifications (recipient_id, id DESC) WHERE seen_at IS NULL;

CREATE TABLE user_feed_states (
    user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    new_feed_seen_at timestamptz NOT NULL DEFAULT NOW()
);

CREATE TABLE user_blocks (
    blocker_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT user_blocks_no_self_block CHECK (blocker_id <> blocked_id)
);
CREATE INDEX idx_user_blocks_blocked_id ON user_blocks (blocked_id);

CREATE TABLE alpha_testers (
    email_hash text PRIMARY KEY,
    university_id uuid NOT NULL REFERENCES universities(id) ON DELETE CASCADE,
    active boolean NOT NULL DEFAULT TRUE,
    expires_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT NOW(),
    CONSTRAINT alpha_testers_email_hash_length CHECK (char_length(email_hash) = 64)
);
CREATE INDEX alpha_testers_active_expiry_idx
    ON alpha_testers (active, expires_at);

CREATE TABLE user_daily_activity (
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    activity_date date NOT NULL,
    university_id uuid NOT NULL REFERENCES universities(id),
    first_seen_at timestamptz NOT NULL DEFAULT NOW(),
    last_seen_at timestamptz NOT NULL DEFAULT NOW(),
    posts_created integer NOT NULL DEFAULT 0 CHECK (posts_created >= 0),
    comments_created integer NOT NULL DEFAULT 0 CHECK (comments_created >= 0),
    votes_changed integer NOT NULL DEFAULT 0 CHECK (votes_changed >= 0),
    PRIMARY KEY (user_id, activity_date)
);
CREATE INDEX idx_user_daily_activity_date
    ON user_daily_activity (activity_date DESC);
CREATE INDEX idx_user_daily_activity_university_date
    ON user_daily_activity (university_id, activity_date DESC);

CREATE TABLE account_deletions (
    user_id uuid PRIMARY KEY,
    requested_at timestamptz NOT NULL DEFAULT NOW(),
    completed_at timestamptz
);
CREATE INDEX account_deletions_pending ON account_deletions (requested_at)
    WHERE completed_at IS NULL;

CREATE TABLE user_hidden_posts (
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id uuid NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, post_id)
);

CREATE TABLE push_installations (
    installation_id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    registration text NOT NULL UNIQUE CHECK (length(registration) BETWEEN 1 AND 4096),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX push_installations_user_id_idx ON push_installations (user_id);

CREATE TABLE alpha_login_settings (
    id smallint PRIMARY KEY CHECK (id = 1),
    password_hash text NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    allow_privileged boolean NOT NULL DEFAULT false,
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- Historical invitation statistics in the Content Desk.
CREATE TABLE referrals (
    invitee_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    inviter_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    is_test boolean NOT NULL DEFAULT false,
    CHECK (invitee_id <> inviter_id)
);
CREATE INDEX referrals_inviter_idx ON referrals (inviter_id);

CREATE TABLE user_emoji_packs (
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pack_id text NOT NULL,
    granted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, pack_id)
);

CREATE TABLE poll_votes (
    post_id uuid NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    option_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);
CREATE INDEX poll_votes_option_preview_idx
    ON poll_votes(post_id, option_id, created_at DESC, user_id);

CREATE FUNCTION guard_poll_post_content() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.poll_options IS NOT NULL AND (
        NEW.body IS DISTINCT FROM OLD.body OR NEW.flair IS DISTINCT FROM OLD.flair OR
        NEW.media IS DISTINCT FROM OLD.media OR NEW.entities IS DISTINCT FROM OLD.entities OR
        NEW.poll_options IS DISTINCT FROM OLD.poll_options
    ) THEN
        RAISE EXCEPTION 'I post con sondaggio non possono essere modificati';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER guard_poll_post_content BEFORE UPDATE ON posts
    FOR EACH ROW EXECUTE FUNCTION guard_poll_post_content();

-- Application tables are accessed by the backend.
ALTER TABLE cities ENABLE ROW LEVEL SECURITY;
ALTER TABLE universities ENABLE ROW LEVEL SECURITY;
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE post_votes ENABLE ROW LEVEL SECURITY;
ALTER TABLE comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE comment_votes ENABLE ROW LEVEL SECURITY;
ALTER TABLE moderation_actions ENABLE ROW LEVEL SECURITY;
ALTER TABLE reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_feed_states ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_blocks ENABLE ROW LEVEL SECURITY;
ALTER TABLE alpha_testers ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_daily_activity ENABLE ROW LEVEL SECURITY;
ALTER TABLE account_deletions ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_hidden_posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE push_installations ENABLE ROW LEVEL SECURITY;
ALTER TABLE alpha_login_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE referrals ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_emoji_packs ENABLE ROW LEVEL SECURITY;
ALTER TABLE poll_votes ENABLE ROW LEVEL SECURITY;

COMMIT;
