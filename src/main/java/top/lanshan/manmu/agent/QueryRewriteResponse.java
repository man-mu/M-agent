package top.lanshan.manmu.agent;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record QueryRewriteResponse(@JsonProperty("optimize_queries") List<String> optimizedQueries) {
}
