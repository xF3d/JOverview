package translate;


import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.resolution.SymbolResolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import file.Config;
import lombok.Getter;

import java.io.File;
import java.util.List;
import java.util.Stack;

public class ResolverUtils {
    @Getter
    private static SymbolResolver resolver;

    public static void initialize(File projectDirectory) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        //creates problems with metrics, might need an alternative fix later, or it is just not necessary
        //typeSolver.add(new ReflectionTypeSolver());

        addJavaParserTypeSolvers(projectDirectory, typeSolver);


        ParserConfiguration config = new ParserConfiguration();

        resolver = new JavaSymbolSolver(typeSolver);

        config.setSymbolResolver(resolver);
        StaticJavaParser.setConfiguration(config);
    }

    private static void addJavaParserTypeSolvers(File projectDirectory, CombinedTypeSolver typeSolver) {

        File mainSource = sourceOf(projectDirectory);
        if (mainSource.exists() && mainSource.isDirectory()) {
            System.out.println("Adding source root: " + projectDirectory.getAbsolutePath());
            typeSolver.add(
                    new JavaParserTypeSolver(
                            mainSource
                    )
            );
        }

        Stack<File> fileList = new Stack<>();
        fileList.add(mainSource);

        TranslatorsConfig cfg = Config.getInstance().getConfig();
        while (!fileList.isEmpty()) {
            File sub = fileList.pop();

            if (cfg.isSkipDir(sub)) continue;

            File srcMainJava = sourceOf(sub);
            if (srcMainJava.exists() && srcMainJava.isDirectory()) {
                System.out.println("Adding source root: " + srcMainJava.getAbsolutePath());
                typeSolver.add(new JavaParserTypeSolver(srcMainJava));
                continue;
            }

            File[] subFiles = sub.listFiles();
            if (subFiles != null) {
                fileList.addAll(List.of(subFiles));
            }
        }
    }

    public static <T> T resolve(Node node, Class<T> clazz) {
        return resolver.resolveDeclaration(node, clazz);
    }

    public static File sourceOf(File file) {
        File srcMainJava = new File(file, "src/main/java");
        if (srcMainJava.exists()) return srcMainJava;

        return new File(file, "src");
    }
}
