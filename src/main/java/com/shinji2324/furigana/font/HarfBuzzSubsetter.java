package com.shinji2324.furigana.font;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

import java.util.Optional;
import java.util.Set;

/**
 * Subsetter de fontes baseado no HarfBuzz.
 *
 * <p>Recebe uma face sfnt e um conjunto de code points e devolve a face reduzida
 * aos glifos correspondentes (mais os que o HarfBuzz mantém por dependência,
 * como {@code .notdef} e alvos de composição). O formato de saída é o mesmo do
 * entrada: um sfnt autônomo.
 *
 * <p>O hinting é descartado ({@code HB_SUBSET_FLAGS_NO_HINTING}) para reduzir o
 * arquivo; os contornos e a tabela {@code name} — necessária para o player casar
 * a fonte com o {@code Fontname} do estilo — são preservados.
 *
 * <p>Qualquer falha do HarfBuzz (biblioteca ausente, entrada inválida, fonte não
 * suportada) resulta em {@code null}, sinalizando ao chamador para embutir a
 * fonte completa.
 */
public final class HarfBuzzSubsetter implements FontSubsetter {

    /** {@code HB_SUBSET_FLAGS_NO_HINTING}: descarta as instruções de hinting. */
    private static final int FLAG_NO_HINTING = 0x1;

    private final HarfBuzzSubsetLibrary.HarfBuzz harfBuzz;

    HarfBuzzSubsetter(HarfBuzzSubsetLibrary.HarfBuzz harfBuzz) {
        this.harfBuzz = harfBuzz;
    }

    /**
     * Carrega a biblioteca nativa e cria o subsetter.
     *
     * @return o subsetter, ou vazio se a biblioteca não estiver disponível
     */
    public static Optional<HarfBuzzSubsetter> create() {
        return HarfBuzzSubsetLibrary.load().map(HarfBuzzSubsetter::new);
    }

    @Override
    public byte[] subset(byte[] data, Set<Integer> codePoints) {
        if (data == null || data.length == 0 || codePoints == null || codePoints.isEmpty()) {
            return null;
        }

        Memory memory = new Memory(data.length);
        memory.write(0, data, 0, data.length);

        Pointer blob = harfBuzz.hb_blob_create(memory, data.length,
                HarfBuzzSubsetLibrary.HB_MEMORY_MODE_DUPLICATE, null, null);
        if (blob == null) {
            return null;
        }

        Pointer face = null;
        Pointer input = null;
        Pointer subsetFace = null;
        Pointer resultBlob = null;
        try {
            face = harfBuzz.hb_face_create(blob, 0);
            input = harfBuzz.hb_subset_input_create_or_fail();
            if (face == null || input == null) {
                return null;
            }

            Pointer unicodes = harfBuzz.hb_subset_input_unicode_set(input);
            if (unicodes == null) {
                return null;
            }
            for (Integer codePoint : codePoints) {
                harfBuzz.hb_set_add(unicodes, codePoint);
            }
            harfBuzz.hb_subset_input_set_flags(input, FLAG_NO_HINTING);

            subsetFace = harfBuzz.hb_subset_or_fail(face, input);
            if (subsetFace == null) {
                return null;
            }

            resultBlob = harfBuzz.hb_face_reference_blob(subsetFace);
            if (resultBlob == null) {
                return null;
            }
            IntByReference length = new IntByReference();
            Pointer output = harfBuzz.hb_blob_get_data(resultBlob, length);
            int size = length.getValue();
            if (output == null || size <= 0) {
                return null;
            }
            return output.getByteArray(0, size);
        } finally {
            if (resultBlob != null) {
                harfBuzz.hb_blob_destroy(resultBlob);
            }
            if (subsetFace != null) {
                harfBuzz.hb_face_destroy(subsetFace);
            }
            if (input != null) {
                harfBuzz.hb_subset_input_destroy(input);
            }
            if (face != null) {
                harfBuzz.hb_face_destroy(face);
            }
            harfBuzz.hb_blob_destroy(blob);
        }
    }
}
