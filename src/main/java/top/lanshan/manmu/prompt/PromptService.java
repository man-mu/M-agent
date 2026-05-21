package top.lanshan.manmu.prompt;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Service
public class PromptService {

	private final ResourceLoader resourceLoader;

	public PromptService(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	public String load(String name) {
		Resource resource = resourceLoader.getResource("classpath:prompts/" + name + ".md");
		try (var inputStream = resource.getInputStream()) {
			return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Failed to load prompt: " + name, e);
		}
	}

}
