package com.shinji2324.furigana.font;

import java.nio.charset.StandardCharsets;

/**
 * Constrói fontes sfnt sintéticas e mínimas para os testes.
 *
 * <p>Cada fonte tem apenas a tabela {@code name} (com um nome de família), o
 * suficiente para exercitar o {@link SfntReader} e o {@link FontLocator} sem
 * depender de fontes instaladas na máquina.
 */
final class TestFonts {

    private TestFonts() {
    }

    /**
     * Monta um sfnt TrueType mínimo com uma única família declarada.
     *
     * @param family nome de família gravado na tabela {@code name}
     * @return bytes do arquivo
     */
    static byte[] sfnt(String family) {
        byte[] text = family.getBytes(StandardCharsets.UTF_16BE);
        int nameTableLength = 6 + 12 + text.length; // cabeçalho + 1 registro + texto
        int nameOffset = 28; // 12 (offset table) + 16 (1 registro de tabela)

        byte[] out = new byte[nameOffset + nameTableLength];
        put32(out, 0, 0x00010000); // scaler type: TrueType
        put16(out, 4, 1); // numTables

        put32(out, 12, 0x6E616D65); // tag 'name'
        put32(out, 20, nameOffset);
        put32(out, 24, nameTableLength);

        put16(out, nameOffset, 0); // format 0
        put16(out, nameOffset + 2, 1); // count
        put16(out, nameOffset + 4, 18); // stringOffset (após o registro)
        put16(out, nameOffset + 6, 3); // platformID: Windows
        put16(out, nameOffset + 8, 1); // encodingID: Unicode BMP
        put16(out, nameOffset + 10, 0x0409); // languageID en-US
        put16(out, nameOffset + 12, 1); // nameID: família
        put16(out, nameOffset + 14, text.length);
        put16(out, nameOffset + 16, 0); // offset dentro do bloco de strings
        System.arraycopy(text, 0, out, nameOffset + 18, text.length);
        return out;
    }

    /**
     * Monta uma coleção ({@code ttcf}) com uma face por família informada.
     *
     * @param families famílias, na ordem das faces
     * @return bytes da coleção
     */
    static byte[] ttc(String... families) {
        byte[][] faces = new byte[families.length][];
        for (int i = 0; i < families.length; i++) {
            faces[i] = sfnt(families[i]);
        }

        int header = 12 + 4 * families.length;
        int[] offsets = new int[families.length];
        int total = header;
        for (int i = 0; i < families.length; i++) {
            offsets[i] = total;
            total += faces[i].length;
        }

        byte[] out = new byte[total];
        put32(out, 0, 0x74746366); // 'ttcf'
        put32(out, 4, 0x00010000); // versão 1.0
        put32(out, 8, families.length);
        for (int i = 0; i < families.length; i++) {
            put32(out, 12 + 4 * i, offsets[i]);
            System.arraycopy(faces[i], 0, out, offsets[i], faces[i].length);
            // Numa coleção, os deslocamentos das tabelas são absolutos: ajusta o
            // campo de offset (faceOffset + 20) do único registro da face.
            int field = offsets[i] + 20;
            put32(out, field, be32(out, field) + offsets[i]);
        }
        return out;
    }

    static int be32(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    static void put16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >> 8);
        data[offset + 1] = (byte) value;
    }

    static void put32(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >> 24);
        data[offset + 1] = (byte) (value >> 16);
        data[offset + 2] = (byte) (value >> 8);
        data[offset + 3] = (byte) value;
    }
}
