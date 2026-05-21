package top.lanshan.manmu.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import top.lanshan.manmu.modelprovider.CurrentModelSelection;
import top.lanshan.manmu.modelprovider.ModelProviderRegistry;
import top.lanshan.manmu.modelprovider.ProviderSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/api/model")
public class ModelProviderController {

	private final ModelProviderRegistry registry;

	public ModelProviderController(ModelProviderRegistry registry) {
		this.registry = registry;
	}

	@GetMapping("/providers")
	public List<ProviderSummary> providers() {
		return registry.providers();
	}

	@GetMapping("/current")
	public CurrentModelSelection current() {
		return registry.current();
	}

	@PostMapping("/providers/{providerId}/key")
	public ResponseEntity<KeyStatusResponse> saveApiKey(@PathVariable String providerId,
			@Valid @RequestBody SaveKeyRequest request) {
		registry.saveApiKey(providerId, request.apiKey());
		return ResponseEntity.ok(new KeyStatusResponse(providerId, true));
	}

	@PostMapping("/switch")
	public CurrentModelSelection switchModel(@Valid @RequestBody SwitchModelRequest request) {
		return registry.switchModel(request.providerId(), request.modelName());
	}

	@PostMapping("/test")
	public Mono<TestModelResponse> testModel(@Valid @RequestBody TestModelRequest request) {
		return Mono.fromCallable(() -> {
			registry.testCurrentModel(request.prompt());
			CurrentModelSelection current = registry.current();
			return new TestModelResponse(current.providerId(), current.modelName(), true);
		}).subscribeOn(Schedulers.boundedElastic());
	}

	public record SaveKeyRequest(@NotBlank String apiKey) {
	}

	public record KeyStatusResponse(String providerId, boolean apiKeyConfigured) {
	}

	public record SwitchModelRequest(@NotBlank String providerId, @NotBlank String modelName) {
	}

	public record TestModelRequest(String prompt) {

		public TestModelRequest {
			if (prompt == null || prompt.isBlank()) {
				prompt = "Reply with ok.";
			}
		}

	}

	public record TestModelResponse(String providerId, String modelName, boolean ok) {
	}

}
