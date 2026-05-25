package top.lanshan.manmu.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryPropertiesTest {

    @Test
    void defaultsEnableMemory() {
        MemoryProperties properties = new MemoryProperties();
        assertThat(properties.isEnabled()).isTrue();
    }

    @Test
    void defaultMaxMessagesIs20() {
        MemoryProperties properties = new MemoryProperties();
        assertThat(properties.getMaxMessages()).isEqualTo(20);
    }

    @Test
    void defaultMaxMessageCharactersIs800() {
        MemoryProperties properties = new MemoryProperties();
        assertThat(properties.getMaxMessageCharacters()).isEqualTo(800);
    }

    @Test
    void settersOverrideDefaults() {
        MemoryProperties properties = new MemoryProperties();
        properties.setEnabled(false);
        properties.setMaxMessages(10);
        properties.setMaxMessageCharacters(500);

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getMaxMessages()).isEqualTo(10);
        assertThat(properties.getMaxMessageCharacters()).isEqualTo(500);
    }
}
