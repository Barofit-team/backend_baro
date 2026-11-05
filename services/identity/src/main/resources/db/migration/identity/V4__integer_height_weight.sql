-- 키·몸무게는 정수만 받는다. 기존 소수 값은 반올림한다(범위 CHECK는 그대로 유지)

ALTER TABLE identity.user_profiles
    ALTER COLUMN height_cm TYPE integer USING round(height_cm),
    ALTER COLUMN weight_kg TYPE integer USING round(weight_kg);
