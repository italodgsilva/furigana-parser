package com.shinji2324.furigana.furigana;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Intermediate model of a subtitle processed with furigana.
 *
 * <p>It is the representation used by the output layers (JSON, ASS, future
 * formats). One entry is added per SRT entry, preserving synchronization.
 */
public class FuriganaDocument {

    private final List<FuriganaEntry> entries;

    /** Creates an empty document. */
    public FuriganaDocument() {
        this.entries = new ArrayList<>();
    }

    /**
     * Creates a document with the given entries.
     *
     * @param entries entries of the document
     */
    public FuriganaDocument(List<FuriganaEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    /**
     * Adds an entry to the document.
     *
     * @param entry entry to be added
     */
    public void addEntry(FuriganaEntry entry) {
        entries.add(entry);
    }

    /**
     * Returns the entries of the document, in a read-only view.
     *
     * @return list of entries, in the order they were added
     */
    public List<FuriganaEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Returns the number of entries in the document.
     *
     * @return number of entries
     */
    public int size() {
        return entries.size();
    }
}