-- 온보딩 프로필. 1:1로 users에 붙고, 탈퇴하면 함께 삭제한다

CREATE TABLE identity.user_profiles
(
    user_id              uuid PRIMARY KEY,
    gender               varchar(10)  NOT NULL,
    birth_year           integer      NOT NULL,
    height_cm            numeric(4, 1) NOT NULL,
    weight_kg            numeric(4, 1) NOT NULL,
    skeletal_muscle_kg   numeric(4, 1),            -- 선택
    body_fat_percent     numeric(3, 1),            -- 선택
    experience_level     varchar(20)  NOT NULL,
    weekly_workout_days  integer      NOT NULL,
    coaching_style       varchar(20)  NOT NULL,
    created_at           timestamptz  NOT NULL,
    updated_at           timestamptz  NOT NULL,
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES identity.users (id),
    CONSTRAINT ck_user_profiles_gender CHECK (gender IN ('MALE', 'FEMALE')),
    CONSTRAINT ck_user_profiles_birth_year CHECK (birth_year >= 1900),
    CONSTRAINT ck_user_profiles_height CHECK (height_cm BETWEEN 100 AND 250),
    CONSTRAINT ck_user_profiles_weight CHECK (weight_kg BETWEEN 20 AND 300),
    CONSTRAINT ck_user_profiles_skeletal_muscle CHECK (skeletal_muscle_kg BETWEEN 5 AND 150),
    CONSTRAINT ck_user_profiles_body_fat CHECK (body_fat_percent BETWEEN 1 AND 70),
    CONSTRAINT ck_user_profiles_experience CHECK (experience_level IN ('NONE', 'BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    CONSTRAINT ck_user_profiles_weekly_days CHECK (weekly_workout_days BETWEEN 1 AND 7),
    CONSTRAINT ck_user_profiles_coaching CHECK (coaching_style IN ('SPARTA', 'GENTLE', 'FRIENDLY'))
);

-- 운동 목표(복수 선택)
CREATE TABLE identity.user_profile_goals
(
    user_id uuid        NOT NULL,
    goal    varchar(20) NOT NULL,
    CONSTRAINT pk_user_profile_goals PRIMARY KEY (user_id, goal),
    CONSTRAINT fk_user_profile_goals_profile FOREIGN KEY (user_id)
        REFERENCES identity.user_profiles (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_user_profile_goals_goal CHECK (goal IN ('WEIGHT_LOSS', 'MUSCLE_GAIN', 'HEALTH_CARE'))
);

-- 닉네임은 온보딩을 마친 활성 사용자끼리만 고유(대소문자 무시).
-- 온보딩 전에는 SNS 닉네임이 겹칠 수 있고, 탈퇴하면 닉네임이 지워져 반납된다
CREATE UNIQUE INDEX uk_users_nickname_onboarded
    ON identity.users (lower(nickname))
    WHERE onboarded AND status = 'ACTIVE';
