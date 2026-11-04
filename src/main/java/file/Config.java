package file;

import ai.AIManager;
import ai.model.APIType;
import ai.model.ModelType;
import lombok.Getter;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;
import translate.TranslatorsConfig;
import translate.VisitorsBuilder;
import translate.complexity.MetricDefinition;
import translate.complexity.MetricDefinitionRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

@Getter
public class Config {
    private static final String CONFIG_NAME = "config.toml";


    @Getter
    private static Config instance = null;

    public static void init() {
        instance = new Config();
        instance.loadFromFile();
        AIManager.update();
    }

    private final TranslatorsConfig config = new TranslatorsConfig();

    private ModelType mainModel;
    private final ArrayList<ModelType> verifyModels = new ArrayList<>();

    private boolean verifyWithMain;
    private long validationRetries;
    private int concurrency;
    private boolean toolCalling;
    private double wordTokenFactor;

    private Path mvnPath;
    private VisitorsBuilder visitorsBuilder;

    public void regenDefaults() {
        try {
            ResourceExtractor.extractToCurrentDirectory(CONFIG_NAME);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        loadFromFile();
    }

    private void loadFromFile() {
        String toml;
        try {
            toml = Files.readString(getConfigPath());
        } catch (IOException e) {
            regenDefaults();
            FileLogger.LOGGER.warning("Invalid or missing config detected");
            e.printStackTrace();
            return;
        }

        TomlParseResult result = Toml.parse(toml);

        if (result.hasErrors()) {
            throw new RuntimeException("Invalid config");
        }

        boolean showMethods = result.getBoolean("uml_translator.show_methods", () -> true);
        boolean showAttributes = result.getBoolean("uml_translator.show_attributes", () -> true);
        boolean showColoredAccessSpecifiers = result.getBoolean("uml_translator.show_colored_access_specifiers", () -> true);
        boolean showAssociations = result.getBoolean("uml_translator.show_associations", () -> true);

        config
                .setShowMethods(showMethods)
                .setShowAttributes(showAttributes)
                .setShowColoredAccessSpecifiers(showColoredAccessSpecifiers)
                .setShowAssociations(showAssociations);

        var wlArray = result.getArrayOrEmpty("translators.package_whitelist");
        for (int i = 0; i < wlArray.size(); i++) {
            config.withWhitelist(wlArray.getString(i));
        }

        var blArray = result.getArrayOrEmpty("translators.package_blacklist");
        for (int i = 0; i < blArray.size(); i++) {
            config.withBlacklist(blArray.getString(i));
        }

        String fileSkipTests = result.getString("translators.skip_tests", () -> "NONE").toUpperCase(Locale.ROOT);
        config.setSkipTests(TranslatorsConfig.TestFiltering.valueOf(fileSkipTests));

        var rawMainType = result.getString("generator.type");
        if (rawMainType != null) {
            APIType mainType = APIType.valueOf(rawMainType.toUpperCase(Locale.ROOT));
            String mainUrl = result.getString("generator.url");
            String modelName = result.getString("generator.name");
            String mainKey = result.getString("generator.key");
            TomlTable tableParams = result.getTableOrEmpty("generator.extra_params");
            this.mainModel = new ModelType(mainType, modelName, mainUrl, mainKey, TomlUtils.toml2map(tableParams));
        }

        var verifyModels = result.getArrayOrEmpty("verify");
        for (int i = 0; i < verifyModels.size(); i++) {
            TomlTable table = verifyModels.getTable(i);
            ModelType verify = ModelType.config(table);

            if (verify != null && verify.isValid()) {
                this.verifyModels.add(verify);
            } else {
                FileLogger.LOGGER.severe("Verifier is invalid: " + verify);
            }
        }

        var metricDefinitions = result.getArrayOrEmpty("metric_definitions");
        for (int i = 0; i < metricDefinitions.size(); i++) {
            TomlTable table = metricDefinitions.getTable(i);
            MetricDefinition definition = MetricDefinition.config(table);

            if (definition != null) {
                MetricDefinitionRegistry.register(definition);
            } else {
                FileLogger.LOGGER.severe("Found invalid metric definition, skipping");
            }
        }

        verifyWithMain = result.getBoolean("ai.verify_with_main", () -> true);
        validationRetries = result.getLong("ai.validation_retries", () -> 3);
        concurrency = Math.toIntExact(result.getLong("ai.concurrency", () -> 1));
        toolCalling = result.getBoolean("ai.tool_calling", () -> false);
        wordTokenFactor = result.getDouble("ai.word_token_factor", () -> 5.0);

        String mvnPathStr = result.getString("build_runner.mvn_path");
        if (mvnPathStr != null) {
            mvnPath = Path.of(mvnPathStr);
        } else {
            mvnPath = null;
        }

        visitorsBuilder = new VisitorsBuilder(
                result.getBoolean("translators.allow_classes", () -> true),
                result.getBoolean("translators.allow_interfaces", () -> true),
                result.getBoolean("translators.allow_records", () -> true),
                result.getBoolean("translators.allow_enums", () -> true),
                result.getBoolean("translators.allow_annotations", () -> true)
        );
    }

    public Path getConfigPath() {
        return Path.of(CONFIG_NAME);
    }
}
