package top.lanshan.manmu.api;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

class AppInfoControllerTest {

	@Test
	void returnsConfiguredCapabilities() {
		AppInfoController controller = new AppInfoController(true, false, true);

		AppInfoController.AppCapabilities capabilities = controller.capabilities();

		assertThat(capabilities.skillEnabled()).isTrue();
		assertThat(capabilities.ragEnabled()).isFalse();
		assertThat(capabilities.mcpEnabled()).isTrue();
	}

	@Test
	void pathStaysUnderAppApi() {
		RequestMapping controllerMapping = AppInfoController.class.getAnnotation(RequestMapping.class);
		GetMapping methodMapping = findCapabilitiesMapping();

		assertThat(controllerMapping.value()).containsExactly("/api/app");
		assertThat(methodMapping.value()).containsExactly("/capabilities");
	}

	private GetMapping findCapabilitiesMapping() {
		try {
			return AppInfoController.class.getDeclaredMethod("capabilities").getAnnotation(GetMapping.class);
		}
		catch (NoSuchMethodException e) {
			throw new AssertionError(e);
		}
	}

}
