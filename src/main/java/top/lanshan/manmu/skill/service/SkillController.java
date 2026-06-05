package top.lanshan.manmu.skill.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.lanshan.manmu.skill.health.SkillHealthResult;
import top.lanshan.manmu.skill.health.SkillHealthService;
import top.lanshan.manmu.skill.health.SkillInvocationHistoryService;
import top.lanshan.manmu.skill.health.SkillInvocationRecord;
import top.lanshan.manmu.skill.market.SkillPackageArchiveService;
import top.lanshan.manmu.skill.market.SkillPackageImportResult;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@RestController
@RequestMapping("/api/skills")
@ConditionalOnProperty(prefix = "mvp.skill", name = "enabled", havingValue = "true")
public class SkillController {

    private static final Logger logger = LoggerFactory.getLogger(SkillController.class);

    private final SkillService skillService;
    private final SkillHealthService healthService;
    private final SkillInvocationHistoryService invocationHistoryService;

    public SkillController(SkillService skillService, SkillHealthService healthService,
            SkillInvocationHistoryService invocationHistoryService) {
        this.skillService = skillService;
        this.healthService = healthService;
        this.invocationHistoryService = invocationHistoryService;
    }

    @GetMapping
    public Mono<ResponseEntity<List<SkillDefinition>>> listSkills() {
        return blocking(() -> ResponseEntity.ok(skillService.listAll()));
    }

    @GetMapping("/{name}")
    public Mono<ResponseEntity<Map<String, Object>>> getSkill(@PathVariable String name) {
        return blocking(() -> {
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

    @GetMapping("/{name}/health")
    public Mono<ResponseEntity<Object>> getSkillHealth(@PathVariable String name) {
        return blocking(() -> healthResponse(name));
    }

    @PostMapping("/{name}/validate")
    public Mono<ResponseEntity<Object>> validateSkill(@PathVariable String name) {
        return blocking(() -> healthResponse(name));
    }

    @GetMapping("/{name}/invocations")
    public Mono<ResponseEntity<Object>> getSkillInvocations(@PathVariable String name,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return blocking(() -> {
            if (skillService.getDefinition(name).isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            List<SkillInvocationRecord> records = invocationHistoryService.recent(name, limit);
            return ResponseEntity.ok((Object) records);
        });
    }

    @PostMapping(path = "/packages/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Object>> importPackage(@RequestPart("file") FilePart file) {
        return DataBufferUtils.join(file.content(), (int) SkillPackageArchiveService.MAX_PACKAGE_BYTES)
                .flatMap(buffer -> blocking(() -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        SkillPackageImportResult result = skillService.importPromptPackage(file.filename(), bytes);
                        return ResponseEntity.status(HttpStatus.CREATED).body((Object) result);
                    } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body((Object) Map.of("error", e.getMessage()));
                    } catch (IOException e) {
                        logger.error("Failed to import Skill package: {}", e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body((Object) Map.of("error", "Failed to import skill package"));
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                }))
                .onErrorResume(DataBufferLimitException.class, e -> Mono.just(ResponseEntity.badRequest()
                        .body((Object) Map.of("error", "Skill package is too large"))));
    }

    @PostMapping(path = "/packages/import-jar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Object>> importJarPackage(@RequestPart("file") FilePart file) {
        return DataBufferUtils.join(file.content(), (int) SkillPackageArchiveService.MAX_PACKAGE_BYTES)
                .flatMap(buffer -> blocking(() -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        SkillPackageImportResult result = skillService.importJarPackage(file.filename(), bytes);
                        return ResponseEntity.status(HttpStatus.CREATED).body((Object) result);
                    } catch (SkillService.JarPluginsDisabledException e) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body((Object) Map.of("error", e.getMessage()));
                    } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body((Object) Map.of("error", e.getMessage()));
                    } catch (IOException e) {
                        logger.error("Failed to import Jar Skill package: {}", e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body((Object) Map.of("error", "Failed to import Jar skill package"));
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                }))
                .onErrorResume(DataBufferLimitException.class, e -> Mono.just(ResponseEntity.badRequest()
                        .body((Object) Map.of("error", "Skill package is too large"))));
    }

    @GetMapping("/{name}/export")
    public Mono<ResponseEntity<Object>> exportPackage(@PathVariable String name) {
        return blocking(() -> {
            try {
                byte[] zip = skillService.exportPromptPackage(name);
                ByteArrayResource resource = new ByteArrayResource(zip);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + name + "-skill.zip\"")
                        .contentType(MediaType.parseMediaType("application/zip"))
                        .contentLength(zip.length)
                        .body((Object) resource);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            } catch (IOException e) {
                logger.error("Failed to export Skill package: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to export skill package"));
            }
        });
    }

    @PostMapping
    public Mono<ResponseEntity<Object>> createSkill(@RequestBody CreateSkillRequest request) {
        return blocking(() -> {
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
        return blocking(() -> {
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
        return blocking(() -> {
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
        return blocking(() -> {
            try {
                SkillDefinition def = skillService.toggle(name);
                return ResponseEntity.ok((Object) def);
            } catch (SkillService.JarPluginsDisabledException e) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            } catch (IOException e) {
                logger.error("Failed to toggle skill: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to write skill files"));
            }
        });
    }

    @PostMapping("/{name}/reload")
    public Mono<ResponseEntity<Object>> reloadSkill(@PathVariable String name) {
        return blocking(() -> {
            try {
                SkillDefinition def = skillService.reload(name);
                return ResponseEntity.ok((Object) def);
            } catch (SkillService.JarPluginsDisabledException e) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            }
        });
    }

    @DeleteMapping("/packages/{name}")
    public Mono<ResponseEntity<Object>> uninstallPackage(@PathVariable String name) {
        return blocking(() -> {
            try {
                skillService.uninstallPackage(name);
                return ResponseEntity.noContent().build();
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            } catch (IOException e) {
                logger.error("Failed to uninstall Skill package: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to uninstall skill package"));
            }
        });
    }

    /**
     * Request body for create/update operations.
     */
    public record CreateSkillRequest(SkillDefinition definition, String promptTemplate) {}

    private ResponseEntity<Object> healthResponse(String name) {
        try {
            SkillHealthResult result = healthService.health(name);
            return ResponseEntity.ok((Object) result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    private <T> Mono<T> blocking(Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}
