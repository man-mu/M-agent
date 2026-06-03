package top.lanshan.manmu.skill.market;

import top.lanshan.manmu.skill.service.SkillDefinition;

import java.util.regex.Pattern;

public final class SkillPackageValidator {

    private static final Pattern SKILL_NAME = Pattern.compile("^[A-Za-z0-9_-]+$");

    private SkillPackageValidator() {
    }

    public static void requireValidSkillName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Skill name must not be blank");
        }
        String trimmed = name.trim();
        if (!trimmed.equals(name) || !SKILL_NAME.matcher(trimmed).matches()
                || trimmed.contains("..") || containsPathSeparator(trimmed) || containsControlCharacter(trimmed)) {
            throw new IllegalArgumentException(
                    "Skill name only supports letters, numbers, hyphen and underscore");
        }
    }

    public static void requireValidDefinition(SkillDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Skill definition must not be null");
        }
        requireValidSkillName(definition.getName());
    }

    public static boolean isValidSkillName(String name) {
        try {
            requireValidSkillName(name);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean containsPathSeparator(String value) {
        return value.indexOf('/') >= 0 || value.indexOf('\\') >= 0;
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
}
