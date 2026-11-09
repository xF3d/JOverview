package ai.reponse;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.Name;
import file.FileLogger;
import translate.component.formatter.MemberFormatter;
import translate.structure.pkg.PackageManager;
import translate.translator.SimpleTranslator;
import util.ProjectJavaParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public record DiffClassResponse(
        String clazz,
        List<String> addedImports,
        List<String> changes,
        List<String> added,
        String explanation,
        List<String> notes
) implements ApplicableDiff {

    public static final DiffClassResponse EMPTY = new DiffClassResponse("null", List.of(), List.of(), List.of(), "null", List.of());

    public static final DiffClassResponse UNCHANGED = new DiffClassResponse("unchanged", List.of(), List.of("// Unchanged"), List.of(), "unchanged", List.of());

    private static final String SEPARATOR = "===============";

    public static DiffClassResponse fromResponse(String clazz, String response) {
        if (response == null) return EMPTY;

        if (response.strip().equals("UNCHANGED")) return UNCHANGED;

        var data = SectionData.parseSections(response);

        if (!data.hasSections("CHANGES", "EXPLANATION")) {
            return EMPTY;
        }

        List<String> toProcess = data.orElse("ADDED", List.of());
        StringBuilder codeBuilder = new StringBuilder();
        toProcess.forEach(it -> codeBuilder.append(it).append("\n"));
        String[] split = codeBuilder.toString().split(Pattern.quote(SEPARATOR));

        return new DiffClassResponse(
                clazz,
                data.orElse("ADDED_IMPORTS", List.of()),
                data.get("CHANGES"),
                Arrays.stream(split).filter(it -> !it.isBlank()).toList(),
                data.get("EXPLANATION").getFirst(),
                data.orElse("NOTES", List.of())
        );
    }

    @Override
    public void apply(SimpleTranslator newTranslator, Path toUpdatePath) {
        if (!isValid()) {
            FileLogger.LOGGER.warning("Invalid Apply Patch Class: Invalid");
            return;
        }


        try {
            Path projectRoot = getSrcRoot(toUpdatePath);
            List<CompilationUnit> addedClasses = parseAdded();

            Path relativePackage = projectRoot.relativize(toUpdatePath).getParent();
            String packageDeclaration = relativePackage.toString().replace(File.separatorChar, '.');
            for (var clazz : addedClasses) {
                applyAddClass(projectRoot, clazz, packageDeclaration);
            }

            CompilationUnit originalCu = newTranslator.getTypeDeclaration(clazz)
                    .findCompilationUnit().orElseThrow();

            TypeDeclaration<?> originalClazz = originalCu.findFirst(
                    TypeDeclaration.class,
                    c -> PackageManager.resolveTypeDeclaration(c).equals(clazz)
            ).orElseThrow();

            NodeList<ImportDeclaration> imports = parseImports();
            if (imports == null) throw new RuntimeException("impossible");
            imports.forEach(originalCu::addImport);

            TypeDeclaration<?> changes = parseChanges();
            if (changes == null) throw new RuntimeException("impossible");

            DiffUtils.getChildren(changes, CallableDeclaration.class).forEach(m -> DiffUtils.addOrReplaceMethod(originalClazz, m));
            DiffUtils.getChildren(changes, TypeDeclaration.class).forEach(t -> DiffUtils.enrichClass(originalClazz, t));
            DiffUtils.getChildren(changes, FieldDeclaration.class).forEach(f  -> DiffUtils.addFieldIfMissing(originalClazz, f));

            Files.writeString(toUpdatePath, originalCu.toString());
        } catch (IOException e) {
            FileLogger.LOGGER.severe("Fail during write to: " + toUpdatePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyAddClass(Path toAdd, CompilationUnit cu, String defaultPackageDeclaration) {
        String packageDecl = cu.getPackageDeclaration()
                .map(PackageDeclaration::getName)
                .map(Name::asString)
                .orElse(null);
        if (packageDecl == null) {
            packageDecl = defaultPackageDeclaration;
            cu.setPackageDeclaration(defaultPackageDeclaration);
        }

        Optional<TypeDeclaration<?>> typeDeclOpt = cu.getTypes().stream()
                .filter(it -> it.getAccessSpecifier() == AccessSpecifier.PUBLIC)
                .findFirst();
        String nameDecl;
        // if public class not present, any name should do, but let's pick the first class
        if (typeDeclOpt.isEmpty()) {
            NodeList<TypeDeclaration<?>> types = cu.getTypes();
            if (types.isEmpty()) {
                return; // nothing or invalid
            }

            //noinspection SequencedCollectionMethodCanBeUsed
            nameDecl = types.get(0).getNameAsString();
        } else {
            // if public class is present, must have the same name
            nameDecl = typeDeclOpt.get().getNameAsString();
        }

        Path newFile;
        if (packageDecl.isEmpty()) {
            newFile = toAdd.resolve(nameDecl + ".java");
        } else {
            newFile = toAdd
                    .resolve(packageDecl.replace(".", File.separator))
                    .resolve(nameDecl + ".java");
        }

        try {
            Files.createDirectories(newFile.getParent());
            Files.writeString(newFile, cu.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path getSrcRoot(Path path) {
        Path current = path;

        while (current != null) {
            Path fileName = current.getFileName();
            if (fileName != null) {
                String stringFilename = fileName.toString();
                if (stringFilename.equals("src") || stringFilename.equals("java")) {
                    return current;
                }
            }

            current = current.getParent();
        }

        return path;
    }

    @Override
    public Path getPath(SimpleTranslator translator) {
        return translator.getTypeDeclaration(clazz)
                .findCompilationUnit().orElseThrow()
                .getStorage().orElseThrow()
                .getPath();
    }

    @Override
    public boolean isEmpty() {
        return changes.isEmpty() && added.isEmpty() && notes.isEmpty();
    }

    @Override
    public boolean isValid() {
        if (isEmpty()) return false;

        return parseChanges() != null && parseAdded().size() == added.size() && parseImports() != null;
    }

    @Override
    public boolean isUnchanged() {
        return this.equals(UNCHANGED);
    }

    public TypeDeclaration<?> parseChanges() {
        var parser = ProjectJavaParser.makeParser().get();
        var allChanges = String.join("\n", changes);
        String className = getClassName();

        // sometimes the AI generates the class wrapping by itself by mistake
        ParseResult<CompilationUnit> cuResult = parser.parse(allChanges);
        if (cuResult.isSuccessful()) {
            CompilationUnit cu = cuResult.getResult().orElseThrow();
            if (cu.getTypes().stream().anyMatch(it -> {
                var fullName = PackageManager.resolveTypeDeclaration(it);

                // doesn't happen often, but better safe than sorry
                if (fullName.equals(clazz)) return true;

                // most of said "validish" changes only have the same class name
                return className.equals(MemberFormatter.fullSimpleName(it));
            })) {
                return cu.getTypes().getFirst().orElseThrow();
            }
        }

        String toParser = makeParsableNested(className, allChanges);
        cuResult = parser.parse(toParser);

        if (cuResult.isSuccessful()) {
            CompilationUnit cu = cuResult.getResult().orElseThrow();

            return cu.findFirst(
                    ClassOrInterfaceDeclaration.class,
                    c -> MemberFormatter.fullSimpleName(c).equals(className)
            ).orElseThrow();
        }

        return null;
    }

    private static String makeParsableNested(String className, String allChanges) {
        String[] split = MemberFormatter.INNER_CLASS_PATTERN.split(className);
        StringBuilder out = new StringBuilder();

        for (String curr : split) {
            out.append("class ")
                    .append(curr)
                    .append(" {\n");
        }

        out.append(allChanges);
        out.append("\n}".repeat(split.length));

        return out.toString();
    }

    public NodeList<ImportDeclaration> parseImports() {
        String toParse = String.join("\n", addedImports) + "\n class DUMMY {}";
        ParseResult<CompilationUnit> parsedImports = ProjectJavaParser.makeParser().get().parse(toParse);

        if (parsedImports.isSuccessful()) {
            return parsedImports.getResult().orElseThrow().getImports();
        }

        return null;
    }

    public List<CompilationUnit> parseAdded() {
        List<CompilationUnit> output = new ArrayList<>(added.size());

        for (var file : added) {
            var cuResult = ProjectJavaParser.makeParser().get().parse(file);
            if (cuResult.isSuccessful()) {
                output.add(
                        cuResult.getResult().orElse(null)
                );
            }
        }

        return output;
    }

    @Override
    public String origin() {
        return clazz;
    }

    @Override
    public ApplicableDiff withNotes(List<String> notes) {
        return new DiffClassResponse(clazz, addedImports, changes, added, explanation, notes);
    }

    @Override
    public String getOriginalCode(SimpleTranslator translator) {
        return translator.getTypeDeclaration(clazz).findCompilationUnit().orElseThrow().toString();
    }

    @Override
    public String asAi() {
        var map = Map.of(
                "ADDED_IMPORTS", addedImports,
                "CHANGES", changes,
                "ADDED", List.of(String.join(SEPARATOR, added)),
                "EXPLANATION", List.of(explanation),
                "NOTES", Collections.<String>emptyList()
        );

        return new SectionData(map).toString();
    }

    public String getClassName() {
        var split = MemberFormatter.PACKAGE_PATTERN.split(clazz);
        return split[split.length - 1];
    }
}
