-- V1: lược đồ đầy đủ cho tải lên tài liệu và hỏi đáp (T03 mục 3.4).
-- Hoàn tác: không có downgrade; môi trường dev dùng `flyway clean` rồi `migrate` lại.
-- ${embeddingDim} = EMBEDDING_DIM lúc chạy migration (bge-m3: 1024).

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TYPE user_role AS ENUM ('member', 'lead', 'admin');
CREATE TYPE user_status AS ENUM ('active', 'disabled');
CREATE TYPE document_status AS ENUM ('processing', 'ready', 'failed');
CREATE TYPE answer_type AS ENUM ('answered', 'partial', 'not_found');
CREATE TYPE message_status AS ENUM ('streaming', 'completed', 'stopped', 'failed');
CREATE TYPE audit_result AS ENUM ('success', 'failure');

CREATE TABLE users (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email                citext      NOT NULL UNIQUE,
    display_name         text        NOT NULL,
    password_hash        text        NOT NULL,
    role                 user_role   NOT NULL DEFAULT 'member',
    status               user_status NOT NULL DEFAULT 'active',
    must_change_password boolean     NOT NULL DEFAULT false,
    failed_attempts      int         NOT NULL DEFAULT 0,
    locked_until         timestamptz NULL,
    last_login_at        timestamptz NULL,
    created_at           timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE sessions (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash   char(64)    NOT NULL UNIQUE,
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX sessions_user_id_idx ON sessions (user_id);

CREATE TABLE documents (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name             text            NOT NULL,
    size_bytes       bigint          NOT NULL,
    sha256           char(64)        NOT NULL,
    storage_key      text            NOT NULL,
    page_count       int             NULL,
    status           document_status NOT NULL DEFAULT 'processing',
    error_code       text            NULL,
    unreadable_pages int[]           NOT NULL DEFAULT '{}',
    uploaded_by      uuid            NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    uploaded_at      timestamptz     NOT NULL DEFAULT now(),
    processed_at     timestamptz     NULL,
    deleted_at       timestamptz     NULL
);
CREATE INDEX documents_active_idx ON documents (uploaded_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE document_pages (
    document_id uuid NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    page        int  NOT NULL,
    text        text NOT NULL,
    PRIMARY KEY (document_id, page)
);

CREATE TABLE chunks (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id uuid NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    page        int  NOT NULL,
    char_start  int  NOT NULL,
    char_end    int  NOT NULL,
    text        text NOT NULL,
    embedding   vector(${embeddingDim}) NULL
);
CREATE INDEX chunks_document_id_idx ON chunks (document_id);
CREATE INDEX chunks_embedding_hnsw_idx ON chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE conversations (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title            text        NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_activity_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX conversations_user_activity_idx ON conversations (user_id, last_activity_at DESC);

CREATE TABLE messages (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id   uuid           NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    question          text           NOT NULL,
    answer            text           NOT NULL DEFAULT '',
    lang              text           NOT NULL,
    answer_type       answer_type    NULL,
    status            message_status NOT NULL DEFAULT 'streaming',
    missing           text           NULL,
    error_code        text           NULL,
    created_at        timestamptz    NOT NULL DEFAULT now(),
    completed_at      timestamptz    NULL,
    ttft_ms           int            NULL,
    total_ms          int            NULL,
    llm_input_tokens  int            NULL,
    llm_output_tokens int            NULL
);
CREATE INDEX messages_conversation_created_idx ON messages (conversation_id, created_at);

-- Trích dẫn giữ document_id kể cả khi tài liệu bị xóa mềm (deleted_at); RESTRICT chặn xóa cứng tài liệu còn được trích dẫn.
-- chunk_id về NULL nếu đoạn bị tính lại; idx/page vẫn đủ để hiển thị nguồn.
CREATE TABLE citations (
    message_id  uuid NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    idx         int  NOT NULL,
    document_id uuid NOT NULL REFERENCES documents (id) ON DELETE RESTRICT,
    page        int  NOT NULL,
    chunk_id    uuid NULL REFERENCES chunks (id) ON DELETE SET NULL,
    PRIMARY KEY (message_id, idx)
);
CREATE INDEX citations_document_id_idx ON citations (document_id);

-- Nhật ký kiểm toán chỉ được thêm. Không đặt khóa ngoại tới users vì ON DELETE SET NULL là một UPDATE.
CREATE TABLE audit_logs (
    id            bigserial PRIMARY KEY,
    ts            timestamptz  NOT NULL DEFAULT now(),
    actor_user_id uuid         NULL,
    action        text         NOT NULL,
    target        text         NULL,
    result        audit_result NOT NULL
);
CREATE INDEX audit_logs_ts_idx ON audit_logs (ts);

-- Chặn UPDATE/DELETE/TRUNCATE bằng trigger (có hiệu lực cả với chủ bảng và superuser) và thu hồi quyền.
CREATE FUNCTION audit_logs_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only (% not allowed)', TG_OP USING ERRCODE = 'insufficient_privilege';
END;
$$;
CREATE TRIGGER audit_logs_no_update_delete BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_append_only();
CREATE TRIGGER audit_logs_no_truncate BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION audit_logs_append_only();
REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs FROM PUBLIC;
REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs FROM CURRENT_USER;

CREATE TABLE events (
    id         bigserial PRIMARY KEY,
    ts         timestamptz NOT NULL DEFAULT now(),
    name       text        NOT NULL,
    user_id    uuid        NULL,
    session_id uuid        NULL,
    props      jsonb       NOT NULL DEFAULT '{}'
);
CREATE INDEX events_ts_idx ON events (ts);
CREATE INDEX events_name_ts_idx ON events (name, ts);
