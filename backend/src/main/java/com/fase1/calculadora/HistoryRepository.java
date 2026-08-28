package com.fase1.calculadora;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HistoryRepository {
    private final Path file;

    public HistoryRepository(Path file) {
        this.file = file;
    }

    public synchronized void append(String jsonRecord) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(
                file,
                jsonRecord + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    public synchronized List<String> last(int limit) throws IOException {
        if (limit <= 0 || !Files.exists(file)) {
            return Collections.emptyList();
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String> result = new ArrayList<>();
        for (int i = lines.size() - 1; i >= 0 && result.size() < limit; i--) {
            String line = lines.get(i).trim();
            if (!line.isEmpty()) {
                result.add(line);
            }
        }
        return result;
    }
}
