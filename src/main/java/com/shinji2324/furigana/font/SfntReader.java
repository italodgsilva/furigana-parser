package com.shinji2324.furigana.font;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Leitor mínimo de arquivos OpenType ({@code .ttf}/{@code .otf}) e de coleções
 * ({@code .ttc}/{@code .otc}).
 *
 * <p>O único dado relevante para a embedação é o nome de família das fontes
 * (tabela {@code name}, IDs 1 e 16), usado para casar a fonte do estilo ASS com
 * um arquivo do sistema. Como um ASS embute uma face por vez, as faces de uma
 * coleção são separadas em fontes autônomas.
 *
 * <p>Este leitor não interpreta os contornos ({@code glyf}/{@code CFF}) nem
 * valida o arquivo: ele apenas reconhece o cabeçalho sfnt, varre o diretório de
 * tabelas em busca de {@code name} e, quando necessário, recorta uma face para
 * um sfnt independente.
 */
final class SfntReader {

    /** Assinatura de coleção de fontes. */
    private static final int TTC_TAG = 0x74746366; // 'ttcf'

    /** Assinatura de fonte OpenType com contornos CFF. */
    private static final int OTTO_TAG = 0x4F54544F; // 'OTTO'

    /** Tag da tabela de nomes. */
    private static final int NAME_TAG = 0x6E616D65; // 'name'

    private SfntReader() {
    }

    /**
     * Uma face individual de um arquivo sfnt.
     *
     * @param families nomes de família declarados (tabela {@code name})
     * @param data     bytes da face (a própria entrada, ou a face recortada de
     *                 uma coleção)
     * @param cff      {@code true} se os contornos são CFF ({@code OTTO})
     * @param faceIndex índice da face na coleção, ou {@code -1} se o arquivo não
     *                 é uma coleção
     */
    record Face(Set<String> families, byte[] data, boolean cff, int faceIndex) {
    }

    /**
     * Lê as faces de um arquivo sfnt ou de uma coleção.
     *
     * @param data conteúdo do arquivo
     * @return faces encontradas (vazio se o conteúdo não for um sfnt conhecido)
     */
    static List<Face> read(byte[] data) {
        List<Face> faces = new ArrayList<>();
        if (data == null || data.length < 12) {
            return faces;
        }
        if (be32(data, 0) == TTC_TAG) {
            readCollection(data, faces);
        } else if (isSfnt(data, 0)) {
            faces.add(new Face(readFamilies(data, 0), data, be32(data, 0) == OTTO_TAG, -1));
        }
        return faces;
    }

    private static void readCollection(byte[] data, List<Face> faces) {
        long numFonts = be32u(data, 8);
        for (int i = 0; i < numFonts; i++) {
            long record = 12 + 4L * i;
            if (record + 4 > data.length) {
                break;
            }
            int offset = (int) be32u(data, (int) record);
            if (!isSfnt(data, offset)) {
                continue;
            }
            byte[] faceData = extract(data, offset);
            faces.add(new Face(readFamilies(faceData, 0), faceData, isCff(faceData), i));
        }
    }

    /**
     * Verifica se o conteúdo no deslocamento informado é um sfnt reconhecido.
     */
    static boolean isSfnt(byte[] data, int offset) {
        if (offset < 0 || offset + 12 > data.length) {
            return false;
        }
        int tag = be32(data, offset);
        return tag == 0x00010000 // TrueType
                || tag == OTTO_TAG // OpenType/CFF
                || tag == 0x74727565 // 'true'
                || tag == 0x74797031; // 'typ1'
    }

    private static boolean isCff(byte[] data) {
        return data.length >= 4 && be32(data, 0) == OTTO_TAG;
    }

    /**
     * Recorta uma face de uma coleção em um sfnt autônomo.
     *
     * <p>Reaproveita a tabela de diretório da face (trocando apenas os
     * deslocamentos das tabelas) e copia os dados correspondentes. Os
     * {@code checksum} por tabela continuam válidos, pois o conteúdo não muda.
     */
    private static byte[] extract(byte[] data, int faceOffset) {
        int numTables = be16(data, faceOffset + 4);
        int headerSize = 12 + 16 * numTables;

        int[] offsets = new int[numTables];
        int[] lengths = new int[numTables];
        int cursor = headerSize;
        for (int i = 0; i < numTables; i++) {
            int record = faceOffset + 12 + 16 * i;
            lengths[i] = (int) be32u(data, record + 12);
            cursor = align4(cursor);
            offsets[i] = cursor;
            cursor = align4(cursor + lengths[i]);
        }

        byte[] out = new byte[cursor];
        System.arraycopy(data, faceOffset, out, 0, 8);
        put16(out, 4, numTables);
        System.arraycopy(data, faceOffset + 6, out, 6, 6);
        for (int i = 0; i < numTables; i++) {
            int record = faceOffset + 12 + 16 * i;
            int target = 12 + 16 * i;
            System.arraycopy(data, record, out, target, 8); // tag + checksum
            put32(out, target + 8, offsets[i]);
            put32(out, target + 12, lengths[i]);
            System.arraycopy(data, (int) be32u(data, record + 8), out, offsets[i], lengths[i]);
        }
        return out;
    }

    /**
     * Extrai os nomes de família (IDs 1 e 16) da tabela {@code name}.
     */
    static Set<String> readFamilies(byte[] data, int sfntOffset) {
        Set<String> families = new LinkedHashSet<>();
        int numTables = be16(data, sfntOffset + 4);
        int nameOffset = -1;
        for (int i = 0; i < numTables; i++) {
            int record = sfntOffset + 12 + 16 * i;
            if (record + 16 > data.length) {
                break;
            }
            if (be32(data, record) == NAME_TAG) {
                nameOffset = (int) be32u(data, record + 8);
                break;
            }
        }
        if (nameOffset < 0 || nameOffset + 6 > data.length) {
            return families;
        }

        int count = be16(data, nameOffset + 2);
        int stringsBase = nameOffset + be16(data, nameOffset + 4);
        for (int i = 0; i < count; i++) {
            int record = nameOffset + 6 + 12 * i;
            if (record + 12 > data.length) {
                break;
            }
            int platform = be16(data, record);
            int nameId = be16(data, record + 6);
            if (nameId != 1 && nameId != 16) {
                continue;
            }
            int length = be16(data, record + 8);
            int start = stringsBase + be16(data, record + 10);
            if (start < 0 || start + length > data.length) {
                continue;
            }
            String value = decodeName(data, start, length, platform);
            if (!value.isBlank()) {
                families.add(value);
            }
        }
        return families;
    }

    private static String decodeName(byte[] data, int start, int length, int platform) {
        // Plataformas 0 (Unicode) e 3 (Windows) usam UTF-16BE; a plataforma 1
        // (Macintosh) codifica nomes latinos em um byte por caractere.
        if (platform == 0 || platform == 3) {
            return new String(data, start, length, StandardCharsets.UTF_16BE);
        }
        return new String(data, start, length, StandardCharsets.ISO_8859_1);
    }

    private static int align4(int value) {
        return (value + 3) & ~3;
    }

    private static int be16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static int be32(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static long be32u(byte[] data, int offset) {
        return be32(data, offset) & 0xFFFFFFFFL;
    }

    private static void put16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >> 8);
        data[offset + 1] = (byte) value;
    }

    private static void put32(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >> 24);
        data[offset + 1] = (byte) (value >> 16);
        data[offset + 2] = (byte) (value >> 8);
        data[offset + 3] = (byte) value;
    }
}
