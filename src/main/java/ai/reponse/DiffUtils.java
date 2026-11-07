package ai.reponse;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import translate.structure.pkg.PackageManager;

import java.util.ArrayList;
import java.util.List;

public class DiffUtils {
    public static <T> List<T> getChildren(ClassOrInterfaceDeclaration n, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (var c : n.getChildNodes()) {
            if (type.isInstance(c)) {
                out.add((T) c);
            }
        }

        return out;
    }

    public static void addOrReplaceMethod(TypeDeclaration<?> typeDefinition, MethodDeclaration newMethod) {
        NodeList<BodyDeclaration<?>> members = typeDefinition.getMembers();

        for (int i = 0; i < members.size(); i++) {
            var m = members.get(i);
            if (m instanceof MethodDeclaration md && md.getSignature().equals(newMethod.getSignature())) {
                members.set(i, newMethod.clone());
                return;
            }
        }

        typeDefinition.addMember(newMethod.clone());
    }

    public static void enrichClass(TypeDeclaration<?> base, TypeDeclaration<?> toEnrich) {
        String targetFullName = PackageManager.resolveTypeDeclaration(toEnrich);
        TypeDeclaration<?> targetClazz = base.findFirst(
                TypeDeclaration.class,
                c -> PackageManager.resolveTypeDeclaration(c).equals(targetFullName)
        ).orElseThrow();


        for (BodyDeclaration<?> member : toEnrich.getMembers()) {

            // Methods
            if (member instanceof MethodDeclaration newMethod) {
                addOrReplaceMethod(targetClazz, newMethod);
                continue;
            }

            // Fields
            if (member.isFieldDeclaration()) {
                var newField = member.asFieldDeclaration();

                var existingField = targetClazz.getMembers().stream()
                        .filter(BodyDeclaration::isFieldDeclaration)
                        .map(BodyDeclaration::asFieldDeclaration)
                        .filter(field -> field.getVariables().stream()
                                .anyMatch(newVar -> newField.getVariables().stream()
                                        .anyMatch(v -> v.getNameAsString()
                                                .equals(newVar.getNameAsString()))))
                        .findFirst();

                if (existingField.isPresent()) {
                    continue;
                }

                targetClazz.addMember(newField.clone());
            }

            // Type decls
            if (member.isTypeDeclaration()) {
                var type = member.asTypeDeclaration();
                enrichClass(targetClazz, type);
            }
        }

    }
}
