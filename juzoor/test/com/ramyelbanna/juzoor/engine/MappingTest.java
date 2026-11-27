package com.ramyelbanna.juzoor.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;

public class MappingTest {

    @Test
    public void renamesClassesMethodsAndSourcePaths() throws Exception {
        File project = tempDir();
        File smali = new File(project, "app/src/main/smali/com/old/Foo.smali");
        write(smali, ".class public Lcom/old/Foo;\n"
                + ".method public secret()Ljava/lang/String;\n"
                + "    invoke-virtual {v0}, Lcom/old/Foo;->secret()Ljava/lang/String;\n"
                + ".end method\n");
        File javaFile = new File(project, "app/src/main/recovered/java/com/old/Foo.java");
        write(javaFile, "package com.old;\npublic class Foo { public String secret() { return null; } }\n");
        File mapping = new File(project, "mapping.txt");
        write(mapping, "com.old.Foo -> com.new.Bar:\n    java.lang.String secret() -> reveal\n");

        Mapping.Result result = Mapping.apply(project, mapping, null);
        assertEquals(1, result.classes);
        assertEquals(1, result.methods);
        File newSmali = new File(project, "app/src/main/smali/com/new/Bar.smali");
        assertTrue(newSmali.isFile());
        String text = new String(java.nio.file.Files.readAllBytes(newSmali.toPath()), "UTF-8");
        assertTrue(text.contains("Lcom/new/Bar;"));
        assertTrue(text.contains("reveal("));
        File newJava = new File(project, "app/src/main/recovered/java/com/new/Bar.java");
        assertTrue(newJava.isFile());
        assertTrue(new String(java.nio.file.Files.readAllBytes(newJava.toPath()), "UTF-8")
                .contains("class Bar"));
    }

    @Test
    public void parsesR8LineNumberedMembers() throws Exception {
        File mapping = File.createTempFile("mapping", ".txt");
        write(mapping, "# compiler: R8\ncom.old.Foo -> a.b:\n"
                + "    1:2:void run():10:11 -> a\n"
                + "    int count -> b\n");
        java.util.Map<String, Mapping.ClassMap> map = Mapping.parse(mapping);
        Mapping.ClassMap cm = map.get("com.old.Foo");
        assertEquals("a.b", cm.newName);
        assertEquals("a", cm.methods.get("run"));
        assertEquals("b", cm.fields.get("count"));
    }

    private static File tempDir() throws Exception {
        File dir = File.createTempFile("juzoor-map", "");
        assertTrue(dir.delete());
        assertTrue(dir.mkdirs());
        dir.deleteOnExit();
        return dir;
    }

    private static void write(File file, String text) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        FileOutputStream out = new FileOutputStream(file);
        out.write(text.getBytes("UTF-8"));
        out.close();
    }
}
