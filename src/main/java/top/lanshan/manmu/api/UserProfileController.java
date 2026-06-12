package top.lanshan.manmu.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.lanshan.manmu.memory.UserProfileEntity;
import top.lanshan.manmu.memory.UserProfileService;
import top.lanshan.manmu.report.ReportResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user-profile")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final ObjectMapper objectMapper;

    public UserProfileController(UserProfileService userProfileService, ObjectMapper objectMapper) {
        this.userProfileService = userProfileService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public Mono<ResponseEntity<ReportResponse<Map<String, Object>>>> getProfile() {
        return Mono.fromCallable(() -> {
            UserProfileEntity entity = userProfileService.getGlobalProfile();
            if (entity == null) {
                Map<String, Object> empty = Map.of(
                    "profile_summary", "",
                    "expertise_level", "",
                    "detail_preference", "",
                    "style_preference", "",
                    "manual_fields", List.of(),
                    "has_profile", false);
                return ResponseEntity.ok(ReportResponse.success("__global__", "OK", empty));
            }
            Map<String, Object> data = Map.of(
                "profile_summary", entity.getProfileSummary() != null ? entity.getProfileSummary() : "",
                "expertise_level", entity.getExpertiseLevel() != null ? entity.getExpertiseLevel() : "",
                "detail_preference", entity.getDetailPreference() != null ? entity.getDetailPreference() : "",
                "style_preference", entity.getStylePreference() != null ? entity.getStylePreference() : "",
                "manual_fields", parseManualFields(entity.getManualFields()),
                "updated_at", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : "",
                "has_profile", true);
            return ResponseEntity.ok(ReportResponse.success("__global__", "OK", data));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping
    public Mono<ResponseEntity<ReportResponse<Map<String, Object>>>> updateProfile(
            @RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            String summary = (String) body.get("profile_summary");
            String expertise = (String) body.get("expertise_level");
            String detail = (String) body.get("detail_preference");
            String style = (String) body.get("style_preference");
            @SuppressWarnings("unchecked")
            List<String> manualFields = (List<String>) body.getOrDefault("manual_fields", List.of());

            UserProfileEntity entity = userProfileService.updateGlobalProfile(
                    summary, expertise, detail, style, manualFields);
            Map<String, Object> data = Map.of(
                "profile_summary", entity.getProfileSummary() != null ? entity.getProfileSummary() : "",
                "expertise_level", entity.getExpertiseLevel() != null ? entity.getExpertiseLevel() : "",
                "detail_preference", entity.getDetailPreference() != null ? entity.getDetailPreference() : "",
                "style_preference", entity.getStylePreference() != null ? entity.getStylePreference() : "",
                "manual_fields", parseManualFields(entity.getManualFields()),
                "updated_at", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : "");
            return ResponseEntity.ok(ReportResponse.success("__global__", "画像已更新", data));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/reset")
    public Mono<ResponseEntity<ReportResponse<Map<String, Object>>>> resetManualFields() {
        return Mono.fromCallable(() -> {
            UserProfileEntity entity = userProfileService.resetGlobalManualFields();
            if (entity == null) {
                return ResponseEntity.ok(ReportResponse.<Map<String, Object>>error("__global__", "暂无画像可重置"));
            }
            Map<String, Object> data = Map.of(
                "manual_fields", List.of(),
                "message", "手动覆盖已重置，下次对话后自动提取将更新全部字段");
            return ResponseEntity.ok(ReportResponse.success("__global__", "已重置", data));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private List<String> parseManualFields(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
