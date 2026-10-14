-- identity schema는 Flyway가 생성한다(createSchemas=true). 다른 schema를 참조하는 FK는 걸지 않는다

CREATE TABLE identity.users
(
    id                uuid PRIMARY KEY, -- UUIDv7, 앱에서 생성
    nickname          varchar(100),
    profile_image_url varchar(2048),
    role              varchar(20)  NOT NULL,
    status            varchar(20)  NOT NULL,
    onboarded         boolean      NOT NULL DEFAULT false,
    last_login_at     timestamptz,
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    withdrawn_at      timestamptz,
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'WITHDRAWN'))
);

CREATE TABLE identity.social_accounts
(
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          uuid         NOT NULL,
    provider         varchar(20)  NOT NULL,
    provider_user_id varchar(255) NOT NULL,
    created_at       timestamptz  NOT NULL,
    CONSTRAINT fk_social_accounts_user FOREIGN KEY (user_id) REFERENCES identity.users (id),
    CONSTRAINT uk_social_accounts_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT ck_social_accounts_provider CHECK (provider IN ('GOOGLE', 'KAKAO'))
);

CREATE INDEX ix_social_accounts_user_id ON identity.social_accounts (user_id);

CREATE TABLE identity.refresh_tokens
(
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id            uuid        NOT NULL,
    session_id         uuid        NOT NULL, -- 기기별 로그인 세션. 재사용 탐지 시 이 단위로 폐기
    token_hash         varchar(64) NOT NULL, -- SHA-256 hex
    expires_at         timestamptz NOT NULL, -- 슬라이딩 만료(30일)
    session_expires_at timestamptz NOT NULL, -- 절대 만료(90일)
    revoked_at         timestamptz,
    created_at         timestamptz NOT NULL,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES identity.users (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_user_id ON identity.refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_session_id ON identity.refresh_tokens (session_id);
CREATE INDEX ix_refresh_tokens_session_expires_at ON identity.refresh_tokens (session_expires_at);
