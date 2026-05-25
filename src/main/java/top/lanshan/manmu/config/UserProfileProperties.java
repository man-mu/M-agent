package top.lanshan.manmu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mvp.memory.user-profile")
public class UserProfileProperties {

    private boolean enabled = true;

    private int maxMessagesForExtraction = 10;

    private int cacheMinutes = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxMessagesForExtraction() {
        return maxMessagesForExtraction;
    }

    public void setMaxMessagesForExtraction(int maxMessagesForExtraction) {
        this.maxMessagesForExtraction = maxMessagesForExtraction;
    }

    public int getCacheMinutes() {
        return cacheMinutes;
    }

    public void setCacheMinutes(int cacheMinutes) {
        this.cacheMinutes = cacheMinutes;
    }
}
