package top.lanshan.manmu.model;

import java.util.List;

public record ResearchPlan(String title, List<ResearchStep> steps) {
}
