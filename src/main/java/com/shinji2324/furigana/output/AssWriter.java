package com.shinji2324.furigana.output;

public class AssWriter {

    public String write(String text) {
        if (text == null || text.isBlank()) {
            return "[Script Info]\n; placeholder\n";
        }

        return "[Script Info]\n" +
                "Title: Furigana Subtitle\n" +
                "ScriptType: v4.00+\n" +
                "WrapStyle: 0\n" +
                "ScaledBorderAndShadow: yes\n\n" +
                "[V4+ Styles]\n" +
                "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n" +
                "Style: Default,Arial,26,16777215,16777215,0,0,-1,0,0,0,100,100,0,0,1,1,0,2,10,10,10,1\n\n" +
                "[Events]\n" +
                "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
                "Dialogue: 0,0:00:00.00,0:00:05.00,Default,,0,0,0,," + text + "\n";
    }
}
