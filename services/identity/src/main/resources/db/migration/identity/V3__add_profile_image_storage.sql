-- 프로필 사진을 바로핏 저장소로 옮긴다. key가 있으면 저장소 객체, 없으면 SNS 외부 URL이다

ALTER TABLE identity.users
    ADD COLUMN profile_image_key    varchar(512),
    ADD COLUMN profile_image_source varchar(20),
    ADD CONSTRAINT ck_users_profile_image_source CHECK (profile_image_source IN ('SOCIAL', 'UPLOADED'));

-- 기존 사진은 모두 가입 때 받은 SNS URL이다
UPDATE identity.users
SET profile_image_source = 'SOCIAL'
WHERE profile_image_url IS NOT NULL;
