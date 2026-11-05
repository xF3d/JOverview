package translate;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import gui.frame.MainFrame;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import translate.complexity.evaluators.system.NumberOfTestsEvaluator;
import translate.component.formatter.MemberFormatter;
import translate.structure.uml.PackageFilter;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Accessors(chain = true)
@Setter
@Getter
@NoArgsConstructor
public class TranslatorsConfig {

    // uml stuff
    private boolean showMethods;
    private boolean showAttributes;
    private boolean showColoredAccessSpecifiers;
    private boolean showAssociations;

    private final PackageFilter packageFilter = new PackageFilter();
    private final Set<String> skipDirectories = new HashSet<>();

    private TestFiltering skipTests;

    public enum TestFiltering {
        NONE {
            @Override
            public boolean isTestClass(String fqn, TypeDeclaration<?> clazz) {
                return false;
            }
        }, NORMAL {
            @Override
            public boolean isTestClass(String fqn, TypeDeclaration<?> clazz) {
                for (var method : clazz.findAll(MethodDeclaration.class)) {
                    if (NumberOfTestsEvaluator.isTestMethod(method)) {
                        return true;
                    }
                }

                return false;
            }
        }, AGGRESSIVE {
            @Override
            public boolean isTestClass(String fqn, TypeDeclaration<?> clazz) {
                if (NONE.isTestClass(fqn, clazz)) return true;

                if (fqn.toLowerCase(Locale.ROOT).contains("test")) return true;

                var path = clazz.findCompilationUnit().orElseThrow().getStorage().orElseThrow().getPath();
                var relative = MainFrame.getSelectedDirectory().toPath().relativize(path);

                return relative.toString().toLowerCase(Locale.ROOT).contains("test");
            }
        };

        abstract public boolean isTestClass(String fqn, TypeDeclaration<?> clazz);
    }

    public TranslatorsConfig withBlacklist(String entry) {
        packageFilter.add2Bl(entry.replace(".", MemberFormatter.PACKAGE_DELIMITER));
        return this;
    }

    public TranslatorsConfig withWhitelist(String entry) {
        packageFilter.add2Wl(entry.replace(".", MemberFormatter.PACKAGE_DELIMITER));
        return this;
    }

    public void addSkipDirectory(String dir) {
        skipDirectories.add(dir);
    }

    public boolean isSkipDir(File file) {
        Path path = file.toPath();

        return StreamSupport.stream(path.spliterator(), false)
                .map(Path::toString)
                .anyMatch(skipDirectories::contains);
    }
}
