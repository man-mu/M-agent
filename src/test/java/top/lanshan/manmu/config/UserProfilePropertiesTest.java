package top.lanshan.manmu.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfilePropertiesTest {

    @Test
    void defaultsEnableUserProfile() {
        UserProfileProperties properties = new UserProfileProperties();
        assertThat(properties.isEnabled()).isTrue();
    }

    @Test
    void defaultMaxMessagesForExtractionIs10() {
        UserProfileProperties properties = new UserProfileProperties();
        assertThat(properties.getMaxMessagesForExtraction()).isEqualTo(10);
    }

    @Test
    void defaultCacheMinutesIs60() {
        UserProfileProperties properties = new UserProfileProperties();
        assertThat(properties.getCacheMinutes()).isEqualTo(60);
    }

    @Test
    void defaultGuideReporterIsTrue() {
        UserProfileProperties properties = new UserProfileProperties();
        assertThat(properties.isGuideReporter()).isTrue();
    }

    @Test
    void settersOverrideDefaults() {
        UserProfileProperties properties = new UserProfileProperties();
        properties.setEnabled(false);
        properties.setMaxMessagesForExtraction(5);
        properties.setCacheMinutes(30);
        properties.setGuideReporter(false);

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getMaxMessagesForExtraction()).isEqualTo(5);
        assertThat(properties.getCacheMinutes()).isEqualTo(30);
        assertThat(properties.isGuideReporter()).isFalse();
    }
}
