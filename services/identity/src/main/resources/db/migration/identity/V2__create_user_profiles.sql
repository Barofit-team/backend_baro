-- 온보딩 프로필. 1:1로 users에 붙고, 탈퇴하면 함께 삭제한다

CREATE TABLE identity.user_profiles
(
    user_id              uuid PRIMARY KEY,
    gender               varchar(10)   NOT NULL,
    birth_year           integer       NOT NULL, -- 나이로 입력받아 출생연도로 저장
    height_cm            numeric(4, 1) NOT NULL,
    weight_kg            numeric(4, 1) NOT NULL,
    goal                 varchar(20)   NOT NULL,
    experience_level     varchar(20)   NOT NULL,
    weekly_workout_days  integer       NOT NULL,
    coaching_style       varchar(20)   NOT NULL,
    created_at           timestamptz   NOT NULL,
    updated_at           timestamptz   NOT NULL,
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES identity.users (id),
    CONSTRAINT ck_user_profiles_gender CHECK (gender IN ('MALE', 'FEMALE')),
    CONSTRAINT ck_user_profiles_birth_year CHECK (birth_year >= 1900),
    CONSTRAINT ck_user_profiles_height CHECK (height_cm BETWEEN 100 AND 250),
    CONSTRAINT ck_user_profiles_weight CHECK (weight_kg BETWEEN 20 AND 300),
    CONSTRAINT ck_user_profiles_goal CHECK (goal IN ('WEIGHT_LOSS', 'STRENGTH', 'ENDURANCE', 'POSTURE')),
    CONSTRAINT ck_user_profiles_experience CHECK (experience_level IN ('NOVICE', 'BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    CONSTRAINT ck_user_profiles_weekly_days CHECK (weekly_workout_days BETWEEN 2 AND 6),
    CONSTRAINT ck_user_profiles_coaching CHECK (coaching_style IN ('FRIENDLY', 'CALM', 'SPARTA', 'ANALYTICAL'))
);

-- 불편한 부위(복수 선택). 해당 없음이면 행이 없다
CREATE TABLE identity.user_profile_discomfort_areas
(
    user_id uuid        NOT NULL,
    area    varchar(20) NOT NULL,
    CONSTRAINT pk_user_profile_discomfort_areas PRIMARY KEY (user_id, area),
    CONSTRAINT fk_user_profile_discomfort_areas_profile FOREIGN KEY (user_id)
        REFERENCES identity.user_profiles (user_id) ON DELETE CASCADE,
    CONSTRAINT ck_user_profile_discomfort_areas_area CHECK (area IN ('NECK_SHOULDER', 'ARM_WRIST', 'BACK', 'LEG_ANKLE'))
);

-- 닉네임은 온보딩을 마친 활성 사용자끼리만 고유(대소문자 무시).
-- 온보딩 전에는 SNS 닉네임이 겹칠 수 있고, 탈퇴하면 닉네임이 지워져 반납된다
CREATE UNIQUE INDEX uk_users_nickname_onboarded
    ON identity.users (lower(nickname))
    WHERE onboarded AND status = 'ACTIVE';
