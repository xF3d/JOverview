package translate.translator;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import file.Config;
import lombok.Getter;
import translate.component.formatter.MemberFormatter;
import translate.structure.complexity.MethodLike;
import translate.structure.pkg.PackageManager;
import translate.translator.exceptions.InvalidMethodLikeException;
import translate.translator.exceptions.InvalidSelectedClassException;
import translate.translator.exceptions.InvalidSelectedConstructorException;
import translate.translator.exceptions.InvalidSelectedMethodException;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class SimpleTranslator implements Translator {
    protected final PackageManager packageManager = new PackageManager();
    protected final MemberFormatter formatter = new MemberFormatter(packageManager);

    protected final Map<String, TypeDeclaration<?>> classMap = new ConcurrentHashMap<>();
    protected final Map<String, Map<String, MethodDeclaration>> methodMap = new ConcurrentHashMap<>();
    protected final Map<String, Map<String, ConstructorDeclaration>> constructorsMap = new ConcurrentHashMap<>();

    protected boolean executed;

    @Override
    public void addNode(Node node) {
        if (!(node instanceof TypeDeclaration<?> decl)) throw new IllegalArgumentException("Should never happen");

        String fullName = PackageManager.resolveTypeDeclaration(decl);
        var skipTests = Config.getInstance().getConfig().getSkipTests();
        if (skipTests.isTestClass(fullName, decl)) {
            return;
        }

        classMap.put(fullName, decl);

        Map<String, MethodDeclaration> methods = methodMap.get(fullName);
        if (methods == null) {
            methods = new ConcurrentHashMap<>();
        }

        for (MethodDeclaration method : decl.getMethods()) {
            methods.put(method.getSignature().asString(), method);
        }

        methodMap.put(fullName, methods);

        Map<String, ConstructorDeclaration> ctors = constructorsMap.get(fullName);
        if (ctors == null) {
            ctors = new ConcurrentHashMap<>();
        }

        for (ConstructorDeclaration ctor : decl.getConstructors()) {
            ctors.put(ctor.getSignature().asString(), ctor);
        }

        constructorsMap.put(fullName, ctors);
    }

    @Override
    public boolean execute(File selectedDirectory) {
        boolean result = Translator.super.execute(selectedDirectory);
        executed = true;
        return result;
    }

    @Override
    public PackageManager getPackageManager() {
        return packageManager;
    }

    @Override
    public boolean hasExecuted() {
        return executed;
    }

    public TypeDeclaration<?> getTypeDeclaration(String fullClassName) throws InvalidSelectedClassException {
        var clazz = classMap.get(fullClassName);
        if (clazz == null) {
            throw new InvalidSelectedClassException(fullClassName);
        }

        return clazz;
    }

    public ConstructorDeclaration getConstructorDeclaration(String fullClassName, String method) throws InvalidSelectedClassException, InvalidSelectedMethodException {
        var ctorMap = constructorsMap.get(fullClassName);
        if (ctorMap == null) {
            throw new InvalidSelectedClassException(fullClassName);
        }

        var result = ctorMap.get(method);
        if (result == null) {
            throw new InvalidSelectedConstructorException(fullClassName, method);
        }

        return result;
    }

    public MethodDeclaration getMethodDeclaration(String fullClassName, String method) throws InvalidSelectedClassException, InvalidSelectedMethodException {
        var mthMap = methodMap.get(fullClassName);
        if (mthMap == null) {
            throw new InvalidSelectedClassException(fullClassName);
        }

        var result = mthMap.get(method);
        if (result == null) {
            throw new InvalidSelectedMethodException(fullClassName, method);
        }

        return result;
    }

    public MethodLike<?> getMethodLike(String fullClassName, String method) throws InvalidMethodLikeException {
        try {
            return MethodLike.of(getMethodDeclaration(fullClassName, method));
        } catch (InvalidSelectedMethodException e) {
            try {
                return MethodLike.of(getConstructorDeclaration(fullClassName, method));
            } catch (IllegalArgumentException ee) {
                throw new InvalidMethodLikeException(fullClassName, method);
            }
        }
    }
}
