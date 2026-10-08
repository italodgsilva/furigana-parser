package com.shinji2324.furigana.subtitle;

public class SubtitleEntry {
    private String id;
    private String start;
    private String end;
    private String text;

    public SubtitleEntry() {
    }

    public SubtitleEntry(String id, String start, String end, String text) {
        this.id = id;
        this.start = start;
        this.end = end;
        this.text = text;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return "SubtitleEntry{" +
                "id='" + id + '\'' +
                ", start='" + start + '\'' +
                ", end='" + end + '\'' +
                ", text='" + text + '\'' +
                '}';
    }
}
