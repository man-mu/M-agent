package top.lanshan.manmu.node;

import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;
import reactor.core.publisher.Flux;

public interface ResearchNode {

	// Runner 用 order() 决定线性执行顺序。这个 MVP 暂时没有真正的图边，所以 order
	// 就是最小可理解版本的“工作流拓扑”。
	int order();

	// name 会写入 ResearchEvent，方便前端展示“当前执行到哪个节点”，也方便日志和测试断言。
	String name();

	// 每个节点都返回 Flux<ResearchEvent>，表示节点执行过程中可以陆续发出多条事件。
	// 比如 ResearcherNode 会先发 started，再为每个研究步骤发 step_completed。
	Flux<ResearchEvent> run(ResearchState state);

}
