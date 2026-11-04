package file;

import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

import java.util.Map;

public class TomlUtils {
    public static Map<String, Object> toml2map(TomlTable table) {
        Map<String, Object> result = table.toMap();
        var keys = result.keySet();

        for (var key : keys) {
            var value = result.get(key);
            if (value instanceof TomlTable valueTable) {
                result.put(key, toml2map(valueTable));
            } else if (value instanceof TomlArray valueArray) {
                result.put(key, valueArray.toList());
            }
        }

        return result;
    }
}
