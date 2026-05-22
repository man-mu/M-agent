package top.lanshan.manmu.agent;

import java.util.List;

public interface QueryRewriteAgent {

	List<String> rewrite(String query, int optimizeQueryNum);

}
