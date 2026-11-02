package translate.translator;

import com.github.javaparser.ParseResult;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.visitor.VoidVisitor;
import file.Config;
import file.DirectoryExplorer;
import file.FileHandler;
import file.FileLogger;
import translate.structure.pkg.PackageManager;
import util.ProjectJavaParser;
import visitors.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public interface Translator extends NodeHandler {

    PackageManager getPackageManager();
    boolean hasExecuted();

    default void handleCompilationUnit(CompilationUnit unit) {

    }

    default void translateFiles(List<File> files) throws FileNotFoundException {
        PackageManagerVisitor packageManagerVisitor = new PackageManagerVisitor(getPackageManager());

        int threadCount = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

        FileLogger.LOGGER.info("File detected: " + files.size());
        long start = System.currentTimeMillis();

        List<CompilationUnit> compilationUnits = new ArrayList<>();
        try(ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<CompletableFuture<CompilationUnit>> futures = files.stream()
                    .map(f -> CompletableFuture.supplyAsync(() -> {
                        var parser = ProjectJavaParser.makeParser();
                        File file = f.getAbsoluteFile();

                        try {
                            ParseResult<CompilationUnit> result =
                                    parser.get().parse(file);

                            if (result.isSuccessful() && result.getResult().isPresent()) {
                                return result.getResult().get();
                            }

                            FileLogger.LOGGER.warning(
                                    "Parsing failed for: " + file.getPath() + "\n"
                            );

                            for (Problem problem : result.getProblems()) {
                                FileLogger.LOGGER.warning(
                                        "Problem: " + problem.getMessage()
                                );

                                problem.getLocation().ifPresent(loc ->
                                        FileLogger.LOGGER.warning(" at " + loc)
                                );
                            }

                        } catch (FileNotFoundException e) {
                            FileLogger.LOGGER.severe(
                                    "File not found: " + file
                            );
                        } catch (Exception e) {
                            FileLogger.LOGGER.severe(
                                    "Error parsing: " + file
                            );
                        }

                        return null;
                    }, executor))
                    .toList();

            futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .forEach(compilationUnits::add);

            long end = System.currentTimeMillis();

            FileLogger.LOGGER.info(
                    "Time to parse sources: " + (end - start)
            );
        }

        start = System.currentTimeMillis();
        // first construct the package map
        for (var cu : compilationUnits) {
            cu.accept(packageManagerVisitor, null);
        }

        long end = System.currentTimeMillis();
        FileLogger.LOGGER.info("Time to create Package Map: " + (end - start));

        start = System.currentTimeMillis();
        // then run the visitors which might depend on it
        for (CompilationUnit cu : compilationUnits) {
            handleCompilationUnit(cu);
            for (VoidVisitor<Void> visitor : Config.getInstance().getVisitorsBuilder().createVisitors(this)) {
                cu.accept(visitor, null);
            }
        }
        end = System.currentTimeMillis();
        FileLogger.LOGGER.info("Time to visit type definitions: " + (end - start));
    }

    default boolean execute(File selectedDirectory) {
        if (hasExecuted()) return true;

        FileHandler handler = new FileHandler(this);

        if (selectedDirectory == null || !selectedDirectory.exists()) {
            return false;
        }

        new DirectoryExplorer(handler).explore(selectedDirectory);
        return true;
    }
}