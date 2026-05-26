ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS expertise_level VARCHAR(32);

ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS detail_preference VARCHAR(32);

ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS style_preference VARCHAR(32);
