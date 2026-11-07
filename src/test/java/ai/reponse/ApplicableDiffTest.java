package ai.reponse;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.Assert;
import org.junit.Test;
import util.ProjectJavaParser;

public class ApplicableDiffTest {

    @Test
    public void test_addOrReplaceMethod() {
        JavaParser parser = ProjectJavaParser.makeParser().get();
        CompilationUnit cuClass = parser.parse("""
                class AAA {
                    void test() {
                    }
                }
                """).getResult().orElseThrow().clone();

        ClassOrInterfaceDeclaration AAAclass = cuClass.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();

        CompilationUnit cuClassToAdd = parser.parse("""
                class BBB {
                    void toAddMethod() {
                    }
                    void test() {
                        int x = 2;
                    }
                }
                """).getResult().orElseThrow().clone();
        ClassOrInterfaceDeclaration BBBclass = cuClassToAdd.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();


        MethodDeclaration toAddMethod = BBBclass.getMethods().getFirst();
        MethodDeclaration testOverride = BBBclass.getMethods().get(1);
        DiffUtils.addOrReplaceMethod(AAAclass, toAddMethod);
        DiffUtils.addOrReplaceMethod(AAAclass, testOverride);

        Assert.assertNotNull(AAAclass.getMethodsByName("toAddMethod"));
    }
}
