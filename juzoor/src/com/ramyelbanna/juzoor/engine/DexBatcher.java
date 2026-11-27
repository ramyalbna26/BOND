package com.ramyelbanna.juzoor.engine;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Splits one DEX into smaller, valid DEX files for memory-bounded decompilation.
 *
 * <p>Jadx keeps the complete class graph of its input in memory. A large
 * {@code classes.dex} can therefore exhaust a phone heap even when only one
 * class is being written at a time. This class rewrites the DEX in batches of
 * complete top-level classes (an outer class always travels with its inner
 * classes), so each Jadx run only sees the batch that it has to decompile.</p>
 *
 * <p>Batches keep the original class names. That matters because the fallback
 * writer and the generated project use those names: a class that Jadx has
 * already written must not appear under a different path when a later batch is
 * processed.</p>
 */
final class DexBatcher {

    static final class Batch {
        private final List<List<ClassDef>> groups;

        Batch(List<List<ClassDef>> groups) {
            this.groups = groups;
        }

        int size() {
            int count = 0;
            for (List<ClassDef> group : groups) count += group.size();
            return count;
        }

        boolean isEmpty() {
            return groups.isEmpty();
        }

        boolean canSplit() {
            return groups.size() > 1;
        }

        /** Splits at a group boundary so an outer and its inner classes stay together. */
        Batch firstHalf() {
            int half = Math.max(1, groups.size() / 2);
            return new Batch(new ArrayList<>(groups.subList(0, half)));
        }

        Batch secondHalf() {
            int half = Math.max(1, groups.size() / 2);
            return new Batch(new ArrayList<>(groups.subList(half, groups.size())));
        }
    }

    static final class Plan {
        private final Opcodes opcodes;
        private final String sourceName;
        final List<Batch> batches;

        Plan(String sourceName, Opcodes opcodes, List<Batch> batches) {
            this.sourceName = sourceName;
            this.opcodes = opcodes;
            this.batches = batches;
        }

        void write(Batch batch, File target) throws IOException {
            DexPool.writeTo(target.getAbsolutePath(), new SubsetDexFile(batch, opcodes));
        }

        String sourceName() {
            return sourceName;
        }
    }

    private DexBatcher() {
    }

    static Plan plan(File dex, int targetClasses) throws IOException {
        DexBackedDexFile file = DexFileFactory.loadDexFile(dex, null);
        List<ClassDef> classes = new ArrayList<>(file.getClasses());
        Set<String> types = new HashSet<>();
        for (ClassDef c : classes) types.add(c.getType());

        // Group inner classes with their outer class.  Jadx only produces a
        // stable source tree when the whole group is present in the same run.
        Map<String, List<ClassDef>> grouped = new LinkedHashMap<>();
        for (ClassDef c : classes) {
            String type = c.getType();
            String root = type;
            int dollar = root.lastIndexOf('$');
            while (dollar > 0) {
                String candidate = root.substring(0, dollar) + ";";
                if (!types.contains(candidate)) break;
                root = candidate;
                dollar = root.lastIndexOf('$');
            }
            List<ClassDef> group = grouped.get(root);
            if (group == null) {
                group = new ArrayList<>();
                grouped.put(root, group);
            }
            group.add(c);
        }

        int target = Math.max(1, targetClasses);
        List<Batch> batches = new ArrayList<>();
        List<List<ClassDef>> current = new ArrayList<>();
        int currentSize = 0;
        for (List<ClassDef> group : grouped.values()) {
            if (!current.isEmpty() && currentSize + group.size() > target) {
                batches.add(new Batch(current));
                current = new ArrayList<>();
                currentSize = 0;
            }
            current.add(group);
            currentSize += group.size();
        }
        if (!current.isEmpty()) batches.add(new Batch(current));
        return new Plan(dex.getName(), file.getOpcodes(), batches);
    }

    private static final class SubsetDexFile implements DexFile {
        private final Batch batch;
        private final Opcodes opcodes;

        SubsetDexFile(Batch batch, Opcodes opcodes) {
            this.batch = batch;
            this.opcodes = opcodes;
        }

        @Override
        public Set<? extends ClassDef> getClasses() {
            Set<ClassDef> out = new LinkedHashSet<>();
            for (List<ClassDef> group : batch.groups) out.addAll(group);
            return out;
        }

        @Override
        public Opcodes getOpcodes() {
            return opcodes;
        }
    }
}
