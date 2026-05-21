package top.lanshan.manmu.modelprovider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class ModelProviderKeyStoreConfiguration {

	@Bean
	ModelProviderKeyStore modelProviderKeyStore(ObjectMapper objectMapper) {
		return new ModelProviderKeyStore(Path.of(".local", "model-providers.json"), objectMapper);
	}

}
