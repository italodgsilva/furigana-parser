package com.shinji2324.furigana.japanese;

public class Token {
    private final String surface;
    private final String kind;

    public Token(String surface) {
        this(surface, "UNKNOWN");
    }

    public Token(String surface, String kind) {
        this.surface = surface;
        this.kind = kind;
    }

    public String getSurface() {
        return surface;
    }

    public String getKind() {
        return kind;
    }

    @Override
    public String toString() {
        return "Token{" +
                "surface='" + surface + '\'' +
                ", kind='" + kind + '\'' +
                '}';
    }
}
