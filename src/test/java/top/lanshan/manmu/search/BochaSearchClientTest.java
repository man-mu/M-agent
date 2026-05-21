package top.lanshan.manmu.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.reactive.function.client.WebClient;
import top.lanshan.manmu.modelprovider.ModelProviderKeyStore;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BochaSearchClientTest {

	@TempDir
	Path tempDir;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void mapsBochaWebPagesIntoSiteInformation() throws Exception {
		BochaSearchClient client = client();
		var response = objectMapper.readTree("""
				{
				  "code": 200,
				  "msg": "success",
				  "data": {
				    "webPages": {
				      "value": [
				        {
				          "name": "Spring Boot 3.4 Release Notes",
				          "url": "https://spring.io/blog/spring-boot-3-4",
				          "snippet": "Spring Boot 3.4 improves observability.",
				          "summary": "Release notes for Spring Boot 3.4.",
				          "siteName": "Spring",
				          "siteIcon": "https://spring.io/favicon.ico",
				          "datePublished": "2024-11-21"
				        }
				      ]
				    }
				  }
				}
				""");

		var results = client.parse(response);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).title()).isEqualTo("Spring Boot 3.4 Release Notes");
		assertThat(results.get(0).url()).isEqualTo("https://spring.io/blog/spring-boot-3-4");
		assertThat(results.get(0).summary()).isEqualTo("Release notes for Spring Boot 3.4.");
		assertThat(results.get(0).siteName()).isEqualTo("Spring");
	}

	@Test
	void buildsOfficialBochaWebSearchRequestBody() {
		BochaSearchProperties properties = new BochaSearchProperties();
		properties.setFreshness("oneWeek");
		properties.setSummary(true);
		properties.setCount(3);
		BochaSearchClient client = new BochaSearchClient(WebClient.builder(), properties,
				new ModelProviderKeyStore(tempDir.resolve("model-providers.json"), objectMapper));

		var body = client.requestBody("Spring Boot WebFlux SSE");

		assertThat(body).containsEntry("query", "Spring Boot WebFlux SSE")
			.containsEntry("freshness", "oneWeek")
			.containsEntry("summary", true)
			.containsEntry("count", 3);
	}

	@Test
	void returnsEmptyListWhenBochaResponseHasNoWebPages() throws Exception {
		BochaSearchClient client = client();

		var results = client.parse(objectMapper.readTree(""" 
				{"code": 200, "data": {}}
				"""));

		assertThat(results).isEmpty();
	}

	private BochaSearchClient client() {
		return new BochaSearchClient(WebClient.builder(), new BochaSearchProperties(),
				new ModelProviderKeyStore(tempDir.resolve("model-providers.json"), objectMapper));
	}

}
