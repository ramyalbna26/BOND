package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies a ProGuard / R8 {@code mapping.txt} to a recovered project.
 *
 * <p>Class names are restored first (including the Smali file path), then
 * methods and fields are renamed in Smali definitions, invocations and the
 * Java/Kotlin skeletons.  The operation is offline, keeps a summary under
 * {@code analysis/mapping-applied.txt}, and never deletes a file it cannot
 * rewrite.</p>
 */
public final class Mapping {

    public static final class ClassMap {
        public final String oldName;
        public final String newName;
        public final Map<String, String> methods = new LinkedHashMap<>();
        public final Map<String, String> fields = new LinkedHashMap<>();

        ClassMap(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }
    }

    public static final class Result {
        public int classes;
        public int methods;
        public int fields;
        public int smaliFiles;
        public int sourceFiles;
        public int renamedPaths;
        public int skipped;
        public String error;
        public File summaryFile;

        public String line() {
            return classes + " classes · " + methods + " methods · " + fields + " fields · "
                    + (smaliFiles + sourceFiles) + " files updated";
        }
    }

    private Mapping() {
    }

    public static Result apply(File project, File mappingFile, LogSink log) {
        Result result = new Result();
        if (project == null || !project.isDirectory()) {
            result.error = "Recovered project not found";
            return result;
        }
        if (mappingFile == null || !mappingFile.isFile()) {
            result.error = "mapping.txt not found";
            return result;
        }
        Map<String, ClassMap> map;
        try {
            map = parse(mappingFile);
        } catch (IOException e) {
            result.error = "Could not read mapping.txt: " + e;
            return result;
        }
        if (map.isEmpty()) {
            result.error = "mapping.txt contains no class mappings";
            return result;
        }
        for (ClassMap c : map.values()) {
            result.classes++;
            result.methods += c.methods.size();
            result.fields += c.fields.size();
        }
        File analysis = new File(project, "analysis");
        analysis.mkdirs();
        StringBuilder summary = new StringBuilder();
        summary.append("# mapping.txt applied\n\n");
        summary.append("Mappings: ").append(result.classes).append(" classes, ")
                .append(result.methods).append(" methods, ")
                .append(result.fields).append(" fields\n\n");

        File main = new File(project, "app/src/main");
        File[] children = main.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() && (child.getName().equals("smali")
                        || child.getName().startsWith("smali_"))) {
                    walkSmali(child, child, map, result, summary, log);
                }
            }
        }
        for (String name : new String[]{"recovered/java", "recovered/kotlin"}) {
            File root = new File(main, name);
            if (root.isDirectory()) {
                walkSource(root, root, map, result, summary, log, name.endsWith("kotlin"));
            }
        }
        try {
            result.summaryFile = new File(analysis, "mapping-applied.txt");
            ZipUtil.writeFile(result.summaryFile, summary.toString());
        } catch (IOException e) {
            result.error = "Could not write mapping summary: " + e;
        }
        if (log != null) {
            log.log(LogManager.SUCCESS, "mapping.txt applied: " + result.line());
        }
        return result;
    }

    /** Parses both classic ProGuard and line-numbered R8 output. */
    public static Map<String, ClassMap> parse(File file) throws IOException {
        Map<String, ClassMap> map = new LinkedHashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8), 65536);
        ClassMap current = null;
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                boolean indented = line.charAt(0) == ' ' || line.charAt(0) == '\t';
                if (!indented && line.endsWith(":")) {
                    int arrow = line.indexOf(" -> ");
                    if (arrow <= 0) continue;
                    String oldName = line.substring(0, arrow).trim();
                    String newName = line.substring(arrow + 4, line.length() - 1).trim();
                    if (oldName.isEmpty() || newName.isEmpty()) continue;
                    current = new ClassMap(oldName, newName);
                    map.put(oldName, current);
                    continue;
                }
                if (!indented || current == null) continue;
                int arrow = line.lastIndexOf(" -> ");
                if (arrow <= 0) continue;
                String declaration = line.substring(0, arrow).trim();
                String newName = line.substring(arrow + 4).trim();
                if (newName.isEmpty()) continue;
                String oldName = memberName(declaration);
                if (oldName == null || oldName.isEmpty()) continue;
                if (declaration.contains("(")) current.methods.put(oldName, newName);
                else current.fields.put(oldName, newName);
            }
        } finally {
            reader.close();
        }
        return map;
    }

    /** Extracts the original member name from a mapping declaration. */
    private static String memberName(String declaration) {
        int paren = declaration.indexOf('(');
        int end = paren >= 0 ? paren : declaration.length();
        int space = declaration.lastIndexOf(' ', end - 1);
        String name = declaration.substring(space + 1, end).trim();
        if (name.isEmpty()) return null;
        return name;
    }

    /* --------------------------------------------------------------- smali */

    private static void walkSmali(File root, File dir, Map<String, ClassMap> map,
                                  Result result, StringBuilder summary, LogSink log) {
        if (log != null && log.isCancelled()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                walkSmali(root, f, map, result, summary, log);
                continue;
            }
            if (!f.getName().endsWith(".smali")) continue;
            String rel = root.toURI().relativize(f.toURI()).getPath();
            String descriptor = rel.substring(0, rel.length() - ".smali".length());
            String dotted = descriptor.replace('/', '.');
            ClassMap cm = lookup(map, dotted);
            try {
                String text = read(f);
                String updated = renameInvocations(text, map);
                if (cm != null) updated = renameClass(updated, cm);
                if (!updated.equals(text)) {
                    write(f, updated);
                    result.smaliFiles++;
                }
                if (cm != null) {
                    String newRel = cm.newName.replace('.', '/') + ".smali";
                    if (!newRel.equals(rel)) {
                        File target = new File(root, newRel);
                        if (!target.exists()) {
                            File parent = target.getParentFile();
                            if (parent != null) parent.mkdirs();
                            if (f.renameTo(target)) result.renamedPaths++;
                            else result.skipped++;
                        } else {
                            result.skipped++;
                        }
                    }
                    summary.append("smali  ").append(cm.oldName).append(" -> ").append(cm.newName).append('\n');
                }
            } catch (Throwable t) {
                result.skipped++;
                if (log != null) log.log(LogManager.WARN, "Mapping skipped " + rel + ": " + t);
            }
        }
    }

    private static String renameClass(String text, ClassMap cm) {
        String out = text.replace("L" + cm.oldName.replace('.', '/') + ";",
                "L" + cm.newName.replace('.', '/') + ";");
        for (Map.Entry<String, String> e : cm.methods.entrySet()) {
            out = out.replaceAll("(?m)^(\\s*\\.method\\s+[^\\n]*?\\s)"
                    + Pattern.quote(e.getKey()) + "(\\()", "$1" + Matcher.quoteReplacement(e.getValue()) + "$2");
        }
        for (Map.Entry<String, String> e : cm.fields.entrySet()) {
            out = out.replaceAll("(?m)^(\\s*\\.field\\s+[^\\n]*?\\s)"
                    + Pattern.quote(e.getKey()) + "(:)", "$1" + Matcher.quoteReplacement(e.getValue()) + "$2");
        }
        return out;
    }

    private static final Pattern INVOKE = Pattern.compile(
            "L([^;\\s]+);->([A-Za-z0-9_$<>]+)([(:])");

    private static String renameInvocations(String text, Map<String, ClassMap> map) {
        Matcher m = INVOKE.matcher(text);
        StringBuffer out = new StringBuffer(text.length());
        boolean changed = false;
        while (m.find()) {
            ClassMap cm = map.get(m.group(1).replace('/', '.'));
            if (cm == null) {
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
                continue;
            }
            String name = m.group(2);
            String newName = "(".equals(m.group(3)) ? cm.methods.get(name) : cm.fields.get(name);
            if (newName == null || newName.equals(name)) {
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
                continue;
            }
            changed = true;
            m.appendReplacement(out, Matcher.quoteReplacement(
                    "L" + m.group(1) + ";->" + newName + m.group(3)));
        }
        m.appendTail(out);
        return changed ? out.toString() : text;
    }

    /* ------------------------------------------------------ java / kotlin */

    private static void walkSource(File root, File dir, Map<String, ClassMap> map,
                                   Result result, StringBuilder summary, LogSink log,
                                   boolean kotlin) {
        if (log != null && log.isCancelled()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                walkSource(root, f, map, result, summary, log, kotlin);
                continue;
            }
            String name = f.getName();
            if (!name.endsWith(".java") && !name.endsWith(".kt")) continue;
            String rel = root.toURI().relativize(f.toURI()).getPath();
            String dotted = rel.substring(0, rel.lastIndexOf('.')).replace('/', '.');
            ClassMap cm = lookup(map, dotted);
            try {
                String text = read(f);
                String updated = text;
                if (cm != null) {
                    String targetName = kotlin ? cm.newName.replace('$', '_') : cm.newName;
                    updated = updated.replace(cm.oldName, targetName);
                    String oldSimple = simple(cm.oldName);
                    String newSimple = simple(targetName);
                    if (!oldSimple.equals(newSimple)) {
                        updated = updated.replaceAll("\\b" + Pattern.quote(oldSimple) + "\\b",
                                Matcher.quoteReplacement(newSimple));
                    }
                    for (Map.Entry<String, String> e : cm.methods.entrySet()) {
                        updated = updated.replaceAll("\\b" + Pattern.quote(e.getKey()) + "(?=\\s*\\()",
                                Matcher.quoteReplacement(e.getValue()));
                    }
                }
                if (!updated.equals(text)) {
                    write(f, updated);
                    result.sourceFiles++;
                }
                if (cm != null) {
                    String ext = kotlin ? ".kt" : ".java";
                    String newDotted = kotlin ? cm.newName.replace('$', '_') : cm.newName;
                    String newRel = newDotted.replace('.', '/') + ext;
                    if (!newRel.equals(rel)) {
                        File target = new File(root, newRel);
                        if (!target.exists()) {
                            File parent = target.getParentFile();
                            if (parent != null) parent.mkdirs();
                            if (f.renameTo(target)) result.renamedPaths++;
                            else result.skipped++;
                        } else {
                            result.skipped++;
                        }
                    }
                    summary.append(kotlin ? "kotlin " : "java   ").append(cm.oldName)
                            .append(" -> ").append(cm.newName).append('\n');
                }
            } catch (Throwable t) {
                result.skipped++;
                if (log != null) log.log(LogManager.WARN, "Mapping skipped " + rel + ": " + t);
            }
        }
    }

    private static ClassMap lookup(Map<String, ClassMap> map, String dotted) {
        ClassMap cm = map.get(dotted);
        if (cm == null) cm = map.get(dotted.replace('$', '_'));
        return cm;
    }

    private static String simple(String dotted) {
        int dot = dotted.lastIndexOf('.');
        return dot >= 0 ? dotted.substring(dot + 1) : dotted;
    }

    private static String read(File f) throws IOException {
        if (f.length() > 64L * 1024L * 1024L) return "";
        return new String(ZipUtil.readFile(f), StandardCharsets.UTF_8);
    }

    private static void write(File f, String text) throws IOException {
        ZipUtil.writeFile(f, text);
    }

    /** Small helper for the UI's mapping picker. */
    public static boolean looksLikeMapping(String name) {
        return name != null && name.toLowerCase(Locale.US).endsWith("mapping.txt");
    }
}
