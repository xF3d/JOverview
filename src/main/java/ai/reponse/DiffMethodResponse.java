package ai.reponse;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import file.FileLogger;
import translate.translator.SimpleTranslator;
import util.ProjectJavaParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public record DiffMethodResponse(
        String clazz,
        String method,
        List<String> newMethods,
        String explanation,
        List<String> notes
) implements ApplicableDiff {
    public static final DiffMethodResponse EMPTY = new DiffMethodResponse("null", "null", List.of(), "null", List.of());

    public static final DiffMethodResponse UNCHANGED = new DiffMethodResponse("unchanged", "unchanged", List.of("// unchanged"), "unchanged", List.of());

    public static DiffMethodResponse fromResponse(String origin, String response) {
        if (origin == null) throw new IllegalArgumentException("Null origin");

        String[] split = origin.split(Pattern.quote("#"));
        if (split.length != 2) throw new IllegalArgumentException("Illegal origin: " + origin);

        return fromResponse(split[0], split[1], response);
    }

    public static DiffMethodResponse fromResponse(String clazz, String method, String response) {
        if (response == null) return EMPTY;

        if (response.strip().equals("UNCHANGED")) return UNCHANGED;

        SectionData data = SectionData.parseSections(response);

        if (!data.hasSections("METHODS", "EXPLANATION")) return EMPTY;

        List<String> methods = data.get("METHODS");
        List<String> notes = data.orElse("NOTES", List.of());

        return new DiffMethodResponse(clazz, method, methods, data.get("EXPLANATION").getFirst(), notes);
    }

    @Override
    public boolean isEmpty() {
        return notes.isEmpty() && newMethods.isEmpty();
    }

    @Override
    public boolean isUnchanged() {
        return this.equals(UNCHANGED);
    }

    public String codeMethods() {
        return String.join("\n", newMethods);
    }

    @Override
    public String origin() {
        return clazz + "#" + method;
    }

    @Override
    public String asAi() {
        var map = Map.of(
                "METHODS", newMethods,
                "NOTES", Collections.<String>emptyList()
        );

        return new SectionData(map).toString();
    }

    @Override
    public ApplicableDiff withNotes(List<String> notes) {
        return new DiffMethodResponse(clazz, method, newMethods, explanation, notes);
    }

    @Override
    public String getOriginalCode(SimpleTranslator translator) {
        return translator.getMethodDeclaration(clazz, method).toString();
    }

    @Override
    public boolean isValid() {
        if (isEmpty()) return false;

        String toParser = "class DUMMY {\n" + codeMethods() + "\n}";
        var cuResult = ProjectJavaParser.makeParser().get().parse(toParser);
        if (!cuResult.isSuccessful()) return false;

        var resultOpt = cuResult.getResult();
        if (resultOpt.isEmpty()) return false;

        var result = resultOpt.get();
        return result.findAll(MethodDeclaration.class)
                .stream()
                .anyMatch(decl -> decl.getSignature().asString().equals(method));
    }

    public List<CallableDeclaration<?>> getAddedMethods() {
        ArrayList<CallableDeclaration<?>> addedMethods = new ArrayList<>();
        String toParse = "class DUMMY {\n" + codeMethods() + "}\n";
        ParseResult<CompilationUnit> result = ProjectJavaParser.makeParser().get().parse(toParse);
        if (result.isSuccessful() && result.getResult().isPresent()) {
            CompilationUnit codeCu = result.getResult().get();
            codeCu.findAll(CallableDeclaration.class, addedMethods::add);
            return addedMethods;
        } else {
            FileLogger.LOGGER.warning("Invalid Apply Patch Method: Parsing");
            return List.of();
        }
    }

    @Override
    public void apply(SimpleTranslator newTranslator, Path toUpdatePath) {
        if (!isValid()) {
            FileLogger.LOGGER.warning("Invalid Apply Patch Method: Invalid");
            return;
        }

        CompilationUnit cu;
        ParseResult<CompilationUnit> parseResult;
        try {
            parseResult = ProjectJavaParser.makeParser().get().parse(toUpdatePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (parseResult.isSuccessful()) {
            cu = parseResult.getResult().orElseThrow();
        } else {
            cu = null;
        }

        MethodDeclaration originalMethod = newTranslator.getMethodDeclaration(clazz, method);
        List<CallableDeclaration<?>> addedMethods = getAddedMethods();

        // find declaring class
        TypeDeclaration<?> clazz = originalMethod.findAncestor(TypeDeclaration.class)
                .flatMap(c -> cu.findFirst(TypeDeclaration.class,
                        cc -> cc.getFullyQualifiedName().equals(c.getFullyQualifiedName())))
                .orElseThrow(() -> new IllegalStateException("Target class not found"));

        for (CallableDeclaration<?> method : addedMethods) {
            DiffUtils.addOrReplaceMethod(clazz, method);
        }

        try {
            Files.writeString(toUpdatePath, cu.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Path getPath(SimpleTranslator translator) {
        return translator.getMethodDeclaration(clazz, method)
                .findCompilationUnit().orElseThrow()
                .getStorage().orElseThrow()
                .getPath();
    }
}
