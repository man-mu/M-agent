package top.lanshan.manmu.skill.health;

import java.time.Instant;
import java.util.List;

public record SkillHealthResult(String name, boolean healthy, String status,
        List<SkillHealthCheck> checks, List<SkillDependencyHealth> dependencies,
        Instant validatedAt) {
}
