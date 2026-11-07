package ai.reponse;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import translate.translator.SimpleTranslator;

import java.nio.file.Path;
import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DiffMethodResponse.class, name = "method"),
        @JsonSubTypes.Type(value = DiffClassResponse.class, name = "class")
})
public interface ApplicableDiff {
    void apply(SimpleTranslator newTranslator, Path toUpdatePath);

    Path getPath(SimpleTranslator translator);

    boolean isEmpty();

    boolean isValid();

    boolean isUnchanged();

    String origin();

    List<String> notes();

    String explanation();

    String asAi();

    ApplicableDiff withNotes(List<String> notes);

    String getOriginalCode(SimpleTranslator translator);

}
