package ai.reponse;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import translate.component.formatter.MemberFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DiffUtils {
    public static <T> List<T> getChildren(TypeDeclaration<?> n, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (var c : n.getChildNodes()) {
            if (type.isInstance(c)) {
                out.add((T) c);
            }
        }

        return out;
    }

    public static void addOrReplaceMethod(TypeDeclaration<?> typeDefinition, CallableDeclaration<?> newMethod) {
        NodeList<BodyDeclaration<?>> members = typeDefinition.getMembers();

        for (int i = 0; i < members.size(); i++) {
            BodyDeclaration<?> m = members.get(i);
            if (m instanceof CallableDeclaration<?> md && md.getSignature().equals(newMethod.getSignature())) {
                CallableDeclaration<?> toAdd = newMethod.clone();
                // AI tends to strip throws, stop it from doing it
                if (newMethod.getThrownExceptions().isEmpty())
                    toAdd.setThrownExceptions(md.getThrownExceptions());

                members.set(i, toAdd);
                return;
            }
        }

        typeDefinition.addMember(newMethod.clone());
    }

    public static void enrichClass(TypeDeclaration<?> base, TypeDeclaration<?> usedToEnrich) {
        String targetFullName = MemberFormatter.fullSimpleName(usedToEnrich);
        TypeDeclaration<?> targetClazz = base.findFirst(
                TypeDeclaration.class,
                c -> MemberFormatter.fullSimpleName(c).equals(targetFullName)
        ).orElse(null);

        if (targetClazz == null) {
            base.addMember(usedToEnrich);
            return;
        }

        if (base instanceof EnumDeclaration baseEd && usedToEnrich instanceof EnumDeclaration usedEd) {
            enrichEnum(baseEd, usedEd);
        }

        for (BodyDeclaration<?> member : usedToEnrich.getMembers()) {
            // Methods
            if (member instanceof CallableDeclaration<?> newMethod) {
                addOrReplaceMethod(targetClazz, newMethod);
                continue;
            }

            // Fields
            if (member.isFieldDeclaration()) {
                FieldDeclaration newField = member.asFieldDeclaration();
                addFieldIfMissing(targetClazz, newField);
            }

            // Type decls
            if (member.isTypeDeclaration()) {
                var type = member.asTypeDeclaration();
                enrichClass(targetClazz, type);
            }
        }

    }

    public static void addFieldIfMissing(TypeDeclaration<?> typeDefinition, FieldDeclaration newField) {

        Optional<FieldDeclaration> existingField = typeDefinition.getMembers().stream()
                .filter(BodyDeclaration::isFieldDeclaration)
                .map(BodyDeclaration::asFieldDeclaration)
                .filter(field -> field.getVariables().stream()
                        .anyMatch(newVar -> newField.getVariables().stream()
                                .anyMatch(v -> v.getNameAsString()
                                        .equals(newVar.getNameAsString()))))
                .findFirst();

        if (existingField.isPresent()) {
            return;
        }

        typeDefinition.addMember(newField.clone());
    }

    private static void enrichEnum(EnumDeclaration base, EnumDeclaration usedToEnrich) {
        var baseEntries = base.getEntries();
        var presentEntries = baseEntries.stream().map(EnumConstantDeclaration::getNameAsString).collect(Collectors.toSet());

        for (EnumConstantDeclaration decl : usedToEnrich.getEntries()) {
            String declNameStr = decl.getNameAsString();
            if (!presentEntries.contains(declNameStr)) {
                base.addEntry(decl);
                continue;
            }

            for (EnumConstantDeclaration declBase : baseEntries) {
                if (declBase.getNameAsString().equals(declNameStr)) {
                    declBase.setClassBody(decl.getClassBody());
                }
            }
        }
    }
}
