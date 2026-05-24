package top.lanshan.manmu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "mvp.rag")
public class RagProperties {

    private boolean enabled = false;

    private int topK = 5;

    private double similarityThreshold = 0.3;

    private final Embedding embedding = new Embedding();

    private final ProfessionalKnowledgeBases professionalKnowledgeBases = new ProfessionalKnowledgeBases();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public ProfessionalKnowledgeBases getProfessionalKnowledgeBases() {
        return professionalKnowledgeBases;
    }

    public static class Embedding {
        private String providerId = "dashscope";
        private String model = "text-embedding-v1";
        private int dimensions = 1536;

        public String getProviderId() { return providerId; }
        public void setProviderId(String providerId) { this.providerId = providerId; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getDimensions() { return dimensions; }
        public void setDimensions(int dimensions) { this.dimensions = dimensions; }
    }

    public static class ProfessionalKnowledgeBases {
        private boolean decisionEnabled = true;
        private List<KnowledgeBase> knowledgeBases = new ArrayList<>();

        public boolean isDecisionEnabled() { return decisionEnabled; }
        public void setDecisionEnabled(boolean decisionEnabled) { this.decisionEnabled = decisionEnabled; }
        public List<KnowledgeBase> getKnowledgeBases() { return knowledgeBases; }
        public void setKnowledgeBases(List<KnowledgeBase> knowledgeBases) { this.knowledgeBases = knowledgeBases; }

        public static class KnowledgeBase {
            private String id;
            private String name;
            private String description;
            private String type = "api";
            private boolean enabled = true;
            private int priority = 100;
            private Api api = new Api();

            public String getId() { return id; }
            public void setId(String id) { this.id = id; }
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public String getDescription() { return description; }
            public void setDescription(String description) { this.description = description; }
            public String getType() { return type; }
            public void setType(String type) { this.type = type; }
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public int getPriority() { return priority; }
            public void setPriority(int priority) { this.priority = priority; }
            public Api getApi() { return api; }
            public void setApi(Api api) { this.api = api; }

            public static class Api {
                private String provider = "dashscope";
                private String url;
                private String apiKey;
                private String model;
                private int timeoutMs = 30000;
                private int maxResults = 5;

                public String getProvider() { return provider; }
                public void setProvider(String provider) { this.provider = provider; }
                public String getUrl() { return url; }
                public void setUrl(String url) { this.url = url; }
                public String getApiKey() { return apiKey; }
                public void setApiKey(String apiKey) { this.apiKey = apiKey; }
                public String getModel() { return model; }
                public void setModel(String model) { this.model = model; }
                public int getTimeoutMs() { return timeoutMs; }
                public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
                public int getMaxResults() { return maxResults; }
                public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
            }
        }
    }

}
