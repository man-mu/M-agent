package top.lanshan.manmu.skill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SkillControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    private SkillFileRepository fileRepo;
    private SkillRegistry registry;
    private SkillService service;
    private SkillController controller;

    @BeforeEach
    void setUp() {
        fileRepo = new SkillFileRepository(tempDir, objectMapper);
        registry = new SkillRegistry(fileRepo);
        service = new SkillService(fileRepo, registry, objectMapper);
        controller = new SkillController(service);
    }

    @Test
    void listSkillsReturnsEmptyInitially() {
        var response = controller.listSkills().block();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void createsAndGetsSkill() {
        SkillDefinition def = new SkillDefinition();
        def.setName("test-skill");
        def.setDescription("A test skill");

        var createReq = new SkillController.CreateSkillRequest(def, "prompt {{x}}");
        var response = controller.createSkill(createReq).block();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var getResp = controller.getSkill("test-skill").block();
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody()).containsKey("definition");
        assertThat(getResp.getBody()).containsEntry("promptTemplate", "prompt {{x}}");
    }

    @Test
    void createReturnsErrorForDuplicate() {
        SkillDefinition def = new SkillDefinition();
        def.setName("dup");
        controller.createSkill(new SkillController.CreateSkillRequest(def, "p"))
                .block();

        var duplicate = controller.createSkill(new SkillController.CreateSkillRequest(def, "p"))
                .block();
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void togglesSkillEnabledStatus() {
        SkillDefinition def = new SkillDefinition();
        def.setName("toggle-skill");
        def.setEnabled(true);
        controller.createSkill(new SkillController.CreateSkillRequest(def, "p")).block();

        var toggleResp = controller.toggleSkill("toggle-skill").block();
        assertThat(toggleResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        var afterToggle = controller.getSkill("toggle-skill").block();
        SkillDefinition toggled = objectMapper.convertValue(
                afterToggle.getBody().get("definition"), SkillDefinition.class);
        assertThat(toggled.isEnabled()).isFalse();
    }

    @Test
    void deletesSkill() {
        SkillDefinition def = new SkillDefinition();
        def.setName("to-delete");
        controller.createSkill(new SkillController.CreateSkillRequest(def, "p"))
                .block();

        var deleteResp = controller.deleteSkill("to-delete").block();
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var getResp = controller.getSkill("to-delete").block();
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updatesSkill() {
        SkillDefinition def = new SkillDefinition();
        def.setName("original");
        controller.createSkill(new SkillController.CreateSkillRequest(def, "old prompt"))
                .block();

        SkillDefinition updated = new SkillDefinition();
        updated.setName("original");
        updated.setDescription("Updated description");
        var updateResp = controller.updateSkill("original",
                new SkillController.CreateSkillRequest(updated, "new prompt"))
                .block();
        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        var getResp = controller.getSkill("original").block();
        assertThat(getResp.getBody().get("promptTemplate")).isEqualTo("new prompt");
    }
}
