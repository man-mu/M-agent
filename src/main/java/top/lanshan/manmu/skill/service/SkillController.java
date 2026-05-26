package top.lanshan.manmu.skill.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private static final Logger logger = LoggerFactory.getLogger(SkillController.class);

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public Mono<ResponseEntity<List<SkillDefinition>>> listSkills() {
        return Mono.fromCallable(() -> ResponseEntity.ok(skillService.listAll()));
    }

    @GetMapping("/{name}")
    public Mono<ResponseEntity<Map<String, Object>>> getSkill(@PathVariable String name) {
        return Mono.fromCallable(() -> {
            SkillDefinition def = skillService.getDefinition(name)
                    .orElse(null);
            if (def == null) {
                return ResponseEntity.notFound().build();
            }
            String content = skillService.getPromptContent(name);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("definition", def);
            result.put("promptTemplate", content);
            return ResponseEntity.ok(result);
        });
    }

    @PostMapping
    public Mono<ResponseEntity<Object>> createSkill(@RequestBody CreateSkillRequest request) {
        return Mono.fromCallable(() -> {
            try {
                SkillDefinition def = skillService.create(request.definition(), request.promptTemplate());
                return ResponseEntity.status(HttpStatus.CREATED).body((Object) def);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            } catch (IOException e) {
                logger.error("Failed to create skill: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to write skill files"));
            }
        });
    }

    @PutMapping("/{name}")
    public Mono<ResponseEntity<Object>> updateSkill(@PathVariable String name,
            @RequestBody CreateSkillRequest request) {
        return Mono.fromCallable(() -> {
            try {
                SkillDefinition def = skillService.update(name, request.definition(), request.promptTemplate());
                return ResponseEntity.ok((Object) def);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            } catch (IOException e) {
                logger.error("Failed to update skill: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to write skill files"));
            }
        });
    }

    @DeleteMapping("/{name}")
    public Mono<ResponseEntity<Object>> deleteSkill(@PathVariable String name) {
        return Mono.fromCallable(() -> {
            try {
                skillService.delete(name);
                return ResponseEntity.noContent().build();
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            } catch (IOException e) {
                logger.error("Failed to delete skill: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to delete skill files"));
            }
        });
    }

    @PatchMapping("/{name}/toggle")
    public Mono<ResponseEntity<Object>> toggleSkill(@PathVariable String name) {
        return Mono.fromCallable(() -> {
            try {
                SkillDefinition def = skillService.toggle(name);
                return ResponseEntity.ok((Object) def);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            } catch (IOException e) {
                logger.error("Failed to toggle skill: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to write skill files"));
            }
        });
    }

    /**
     * Request body for create/update operations.
     */
    public record CreateSkillRequest(SkillDefinition definition, String promptTemplate) {}
}
