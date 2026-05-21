package top.lanshan.manmu.modelprovider;

import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

public interface ModelProvider {

	String providerId();

	String displayName();

	List<String> supportedModels();

	ChatModel createChatModel(String modelName, String apiKey);

}
