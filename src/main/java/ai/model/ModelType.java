package ai.model;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import file.TomlUtils;
import org.tomlj.TomlTable;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

public record ModelType(APIType type, String modelName, String url, String apiKey, Map<String, Object> extraParams) {
    public static ModelType config(TomlTable table) {
        try {
            APIType verifyType = APIType.valueOf(table.getString("type").toUpperCase(Locale.ROOT));
            String verifyUrl = table.getString("url");
            String verifyName = table.getString("name");
            String verifyKey = table.getString("key");
            TomlTable extraParams = table.getTableOrEmpty("extra_params");

            return new ModelType(verifyType, verifyName, verifyUrl, verifyKey, TomlUtils.toml2map(extraParams));
        } catch (Exception ingored) {
            return null;
        }
    }

    public boolean isValid() {
        return type != null && modelName != null && url != null;
    }

    public ChatModel build() {
        if (type == APIType.OLLAMA) return OllamaChatModel.builder()
                .modelName(modelName)
                .baseUrl(url)
                .timeout(Duration.ofMinutes(5))
                .build();
        return OpenAiChatModel.builder()
                .modelName(modelName)
                .baseUrl(url)
                .parallelToolCalls(true)
                .apiKey(apiKey)
                .customParameters(extraParams)
                .timeout(Duration.ofMinutes(5))
                .build();
    }
}
