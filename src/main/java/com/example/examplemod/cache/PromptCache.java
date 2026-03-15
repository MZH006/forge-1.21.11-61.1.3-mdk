package com.example.examplemod.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Caches GLB files on disk keyed by a normalized prompt string.
 *
 * Layout:
 *   config/aibuilder/cache/<normalized-prompt>.glb
 *
 * Similarity: two prompts are considered similar if their Jaccard similarity
 * over word sets exceeds a threshold (default 0.6 = 60% word overlap).
 * This catches "a red castle", "red castle", "castle red" as the same thing.
 */
public class PromptCache {

    private static final Path CACHE_DIR = Path.of("config", "aibuilder", "cache");
    private static final double SIMILARITY_THRESHOLD = 0.6;

    /**
     * Returns cached GLB bytes for an exact or similar prompt, or null if none found.
     */
    public byte[] get(String prompt) {
        String normalized = normalize(prompt);

        // 1. Exact match first
        Path exact = CACHE_DIR.resolve(normalized + ".glb");
        if (Files.exists(exact)) {
            System.out.println("[Cache] Exact hit for: " + prompt);
            return readBytes(exact);
        }

        // 2. Fuzzy match — scan cache dir for similar prompts
        try {
            if (!Files.exists(CACHE_DIR)) return null;
            try (Stream<Path> files = Files.list(CACHE_DIR)) {
                return files
                        .filter(p -> p.toString().endsWith(".glb"))
                        .filter(p -> similarity(normalized, stem(p)) >= SIMILARITY_THRESHOLD)
                        .findFirst()
                        .map(p -> {
                            System.out.println("[Cache] Fuzzy hit: '" + prompt + "' ~ '" + stem(p) + "'");
                            return readBytes(p);
                        })
                        .orElse(null);
            }
        } catch (IOException e) {
            System.out.println("[Cache] Error scanning cache: " + e.getMessage());
            return null;
        }
    }

    /**
     * Saves GLB bytes to the cache under the given prompt key.
     */
    public void put(String prompt, byte[] glbBytes) {
        try {
            Files.createDirectories(CACHE_DIR);
            Path dest = CACHE_DIR.resolve(normalize(prompt) + ".glb");
            Files.write(dest, glbBytes);
            System.out.println("[Cache] Saved " + glbBytes.length + " bytes for: " + prompt);
        } catch (IOException e) {
            System.out.println("[Cache] Failed to save: " + e.getMessage());
        }
    }

    /**
     * Normalize a prompt to a safe filename:
     * lowercase, trim, collapse spaces, remove non-alphanumeric except spaces.
     */
    private String normalize(String prompt) {
        return prompt.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9 ]", "")
                .replaceAll("\\s+", "_");
    }

    /** Filename without extension — this is the stored normalized prompt. */
    private String stem(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".glb") ? name.substring(0, name.length() - 4) : name;
    }

    /**
     * Jaccard similarity between two normalized prompt strings (treated as word sets).
     * Score of 1.0 = identical words, 0.0 = no words in common.
     */
    private double similarity(String a, String b) {
        Set<String> wordsA = new HashSet<>(Arrays.asList(a.split("_")));
        Set<String> wordsB = new HashSet<>(Arrays.asList(b.split("_")));

        Set<String> intersection = new HashSet<>(wordsA);
        intersection.retainAll(wordsB);

        Set<String> union = new HashSet<>(wordsA);
        union.addAll(wordsB);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            System.out.println("[Cache] Failed to read: " + e.getMessage());
            return null;
        }
    }
}
