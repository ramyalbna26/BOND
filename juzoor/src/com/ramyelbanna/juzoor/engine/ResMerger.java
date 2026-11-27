package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* loaded from: /workspace/apk_extract/classes.dex */
public final class ResMerger {
    private static final Pattern NAME = Pattern.compile("name=\"([^\"]+)\"");

    private ResMerger() {
    }

    public static int merge(File file, File file2, LogSink logSink) throws IOException {
        File[] listFiles;
        File[] fileArr;
        int i;
        int lastIndexOf;
        int i2;
        String str;
        if (file == null || !file.isDirectory() || (listFiles = file.listFiles()) == null) {
            return 0;
        }
        int length = listFiles.length;
        int i3 = 0;
        int i4 = 0;
        while (i3 < length) {
            File file3 = listFiles[i3];
            if (file3.isDirectory() && file3.getName().startsWith("values")) {
                File file4 = new File(file2, file3.getName());
                File[] listFiles2 = file3.listFiles();
                if (listFiles2 != null) {
                    int length2 = listFiles2.length;
                    int i5 = 0;
                    while (i5 < length2) {
                        File file5 = listFiles2[i5];
                        if (file5.isFile() && file5.getName().endsWith(".xml")) {
                            File file6 = new File(file4, file5.getName());
                            if (!file6.exists()) {
                                if (!file4.exists() && !file4.mkdirs()) {
                                    throw new IOException("Cannot create " + String.valueOf(file4));
                                }
                                ZipUtil.copy(file5, file6);
                                i4 += countEntries(read(file5));
                            } else {
                                String read = read(file6);
                                String read2 = read(file5);
                                Set<String> names = names(read);
                                ArrayList<String> arrayList = new ArrayList();
                                for (String str2 : elements(read2)) {
                                    File[] fileArr2 = listFiles;
                                    Matcher matcher = NAME.matcher(str2);
                                    if (matcher.find()) {
                                        i2 = length;
                                        str = matcher.group(1);
                                    } else {
                                        i2 = length;
                                        str = null;
                                    }
                                    if (str != null && !names.contains(str)) {
                                        names.add(str);
                                        arrayList.add(str2);
                                    }
                                    listFiles = fileArr2;
                                    length = i2;
                                }
                                fileArr = listFiles;
                                i = length;
                                if (!arrayList.isEmpty() && (lastIndexOf = read.lastIndexOf("</resources>")) >= 0) {
                                    StringBuilder sb = new StringBuilder(read.length() + 512);
                                    sb.append((CharSequence) read, 0, lastIndexOf);
                                    for (String str3 : arrayList) {
                                        sb.append("    ");
                                        sb.append(str3);
                                        sb.append('\n');
                                    }
                                    sb.append(read.substring(lastIndexOf));
                                    write(file6, sb.toString());
                                    i4 += arrayList.size();
                                }
                                i5++;
                                listFiles = fileArr;
                                length = i;
                            }
                        }
                        fileArr = listFiles;
                        i = length;
                        i5++;
                        listFiles = fileArr;
                        length = i;
                    }
                }
            }
            i3++;
            listFiles = listFiles;
            length = length;
        }
        if (logSink != null && i4 > 0) {
            logSink.log(LogManager.DEBUG, "Merged " + i4 + " resource entries from a split");
        }
        return i4;
    }

    static List<String> elements(String str) {
        int indexOf;
        ArrayList arrayList = new ArrayList();
        int indexOf2 = str.indexOf("<resources");
        if (indexOf2 < 0 || (indexOf = str.indexOf(62, indexOf2)) < 0) {
            return arrayList;
        }
        int i = indexOf + 1;
        int length = str.length();
        while (i < length) {
            while (i < length && Character.isWhitespace(str.charAt(i))) {
                i++;
            }
            if (i >= length || str.charAt(i) != '<' || str.startsWith("</", i)) {
                break;
            }
            int i2 = i + 1;
            while (i2 < length && !Character.isWhitespace(str.charAt(i2)) && str.charAt(i2) != '>' && str.charAt(i2) != '/') {
                i2++;
            }
            int i3 = 1;
            while (i2 < length && i3 > 0) {
                int indexOf3 = str.indexOf(60, i2);
                if (indexOf3 >= 0) {
                    if (str.startsWith("</", indexOf3)) {
                        i3--;
                        int indexOf4 = str.indexOf(62, indexOf3);
                        if (indexOf4 >= 0) {
                            i2 = indexOf4 + 1;
                            if (i3 == 0) {
                                break;
                            }
                        }
                    } else {
                        int indexOf5 = str.indexOf(62, indexOf3);
                        if (indexOf5 >= 0) {
                            if (!(str.charAt(indexOf5 + (-1)) == '/')) {
                                i3++;
                            }
                            i2 = indexOf5 + 1;
                        }
                    }
                }
                i2 = length;
                break;
            }
            arrayList.add(str.substring(i, Math.min(i2, length)).trim());
            i = i2;
        }
        return arrayList;
    }

    private static Set<String> names(String str) {
        LinkedHashSet linkedHashSet = new LinkedHashSet();
        Iterator<String> it = elements(str).iterator();
        while (it.hasNext()) {
            Matcher matcher = NAME.matcher(it.next());
            if (matcher.find()) {
                linkedHashSet.add(matcher.group(1));
            }
        }
        return linkedHashSet;
    }

    private static int countEntries(String str) {
        return elements(str).size();
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void write(File file, String str) throws IOException {
        Files.write(file.toPath(), str.getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
    }
}
