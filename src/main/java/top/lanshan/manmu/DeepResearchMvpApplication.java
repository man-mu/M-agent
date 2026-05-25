package top.lanshan.manmu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import top.lanshan.manmu.config.MemoryProperties;
import top.lanshan.manmu.config.RagProperties;
import top.lanshan.manmu.config.UserProfileProperties;

@SpringBootApplication
@EnableConfigurationProperties({RagProperties.class, MemoryProperties.class, UserProfileProperties.class})
public class DeepResearchMvpApplication {

	public static void main(String[] args) {
		SpringApplication.run(DeepResearchMvpApplication.class, args);
	}

}
