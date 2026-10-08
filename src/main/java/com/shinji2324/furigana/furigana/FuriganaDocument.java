package com.shinji2324.furigana.furigana;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modelo intermediário de uma legenda processada com furigana.
 *
 * <p>É a representação utilizada pelas camadas de saída (JSON, ASS, futuros
 * formatos). Uma entrada é adicionada por entrada SRT, preservando sincronização.
 */
public class FuriganaDocument {

    private final List<FuriganaEntry> entries;

    /** Cria um documento vazio. */
    public FuriganaDocument() {
        this.entries = new ArrayList<>();
    }

    /**
     * Cria um documento com as entradas informadas.
     *
     * @param entries entradas do documento
     */
    public FuriganaDocument(List<FuriganaEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    /**
     * Adiciona uma entrada ao documento.
     *
     * @param entry entrada a ser adicionada
     */
    public void addEntry(FuriganaEntry entry) {
        entries.add(entry);
    }

    /**
     * Retorna as entradas do documento, em uma visão somente leitura.
     *
     * @return lista de entradas, na ordem em que foram adicionadas
     */
    public List<FuriganaEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Retorna a quantidade de entradas do documento.
     *
     * @return número de entradas
     */
    public int size() {
        return entries.size();
    }
}