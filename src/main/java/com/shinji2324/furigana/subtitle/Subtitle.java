package com.shinji2324.furigana.subtitle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Subtitle {
    private final List<SubtitleEntry> entries;

    public Subtitle() {
        this.entries = new ArrayList<>();
    }

    public Subtitle(List<SubtitleEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    public void addEntry(SubtitleEntry entry) {
        entries.add(entry);
    }

    public List<SubtitleEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public int size() {
        return entries.size();
    }
}
