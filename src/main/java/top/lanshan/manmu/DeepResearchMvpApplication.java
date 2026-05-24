package top.lanshan.manmu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import top.lanshan.manmu.config.RagProperties;

@SpringBootApplication
@EnableConfigurationProperties(RagProperties.class)
public class DeepResearchMvpApplication {

	public static void main(String[] args) {
		SpringApplication.run(DeepResearchMvpApplication.class, args);
	}

}
