package top.lanshan.manmu.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.serializer.std.SpringAIStateSerializer;
import top.lanshan.manmu.model.ResearchEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

final class ResearchGraphStateSerializer extends SpringAIStateSerializer {

	@Override
	public OverAllState cloneObject(OverAllState state) throws IOException, ClassNotFoundException {
		return new OverAllState(copyData(state.data()));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> copyData(Map<String, Object> data) {
		Map<String, Object> copy = new LinkedHashMap<>(data);
		Object events = copy.get(ResearchGraphStateKeys.EVENTS);
		if (events == null) {
			return copy;
		}
		if (events instanceof Collection<?> eventCollection) {
			for (Object event : eventCollection) {
				if (!(event instanceof ResearchEvent)) {
					throw new IllegalStateException("Graph state value '" + ResearchGraphStateKeys.EVENTS
							+ "' contains a non-ResearchEvent value");
				}
			}
			copy.put(ResearchGraphStateKeys.EVENTS,
					new ArrayList<>((Collection<ResearchEvent>) eventCollection));
			return copy;
		}
		throw new IllegalStateException("Graph state value '" + ResearchGraphStateKeys.EVENTS + "' is not a List");
	}

}
