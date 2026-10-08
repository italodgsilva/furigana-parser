package com.shinji2324.furigana.reading;

public class Reading {
    private final String surface;
    private final String reading;
    private final String source;

    public Reading(String surface, String reading) {
        this(surface, reading, "unknown");
    }

    public Reading(String surface, String reading, String source) {
        this.surface = surface;
        this.reading = reading;
        this.source = source;
    }

    public String getSurface() {
        return surface;
    }

    public String getReading() {
        return reading;
    }

    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "Reading{" +
                "surface='" + surface + '\'' +
                ", reading='" + reading + '\'' +
                ", source='" + source + '\'' +
                '}';
    }
}
