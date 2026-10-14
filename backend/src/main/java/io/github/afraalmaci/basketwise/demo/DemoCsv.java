package io.github.afraalmaci.basketwise.demo;

import io.github.afraalmaci.basketwise.normalization.BaseUnit;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads the semicolon-separated demo resources. Lines starting with '#' are comments.
 * Semicolons are used because Dutch decimals use commas.
 */
public final class DemoCsv {

    private DemoCsv() {
    }

    public static List<CatalogEntry> readCatalog(Reader reader) {
        return rows(reader, 6).stream()
                .map(cols -> new CatalogEntry(
                        cols[0],
                        cols[1],
                        cols[2],
                        BaseUnit.fromCode(cols[3]),
                        Arrays.stream(cols[4].split("\\|")).map(CatalogEntry.PackSize::parse).toList(),
                        Long.parseLong(cols[5])))
                .toList();
    }

    public static List<DemoStore> readStores(Reader reader) {
        return rows(reader, 4).stream()
                .map(cols -> new DemoStore(
                        cols[0],
                        cols[1],
                        Double.parseDouble(cols[2]),
                        Double.parseDouble(cols[3])))
                .toList();
    }

    private static List<String[]> rows(Reader reader, int expectedColumns) {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader buffered = new BufferedReader(reader)) {
            String line;
            int lineNumber = 0;
            while ((line = buffered.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] cols = line.split(";", -1);
                if (cols.length != expectedColumns) {
                    throw new IllegalArgumentException(
                            "Line " + lineNumber + ": expected " + expectedColumns + " columns, got " + cols.length);
                }
                for (int i = 0; i < cols.length; i++) {
                    cols[i] = cols[i].trim();
                }
                rows.add(cols);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }
}
