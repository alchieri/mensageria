package com.br.alchieri.consulting.mensageria.util;

import java.text.Normalizer;

import org.springframework.stereotype.Component;

@Component
public class StringUtils {

    /**
     * Normaliza um nome amigável para um formato técnico compatível com a API da Meta
     * (letras minúsculas, underscores, sem acentos ou caracteres especiais).
     * @param friendlyName O nome amigável (ex: "Contato Alchieri v1").
     * @return O nome normalizado (ex: "contato_alchieri_v1").
     */
    public static String normalizeMetaName(String friendlyName) {
        if (friendlyName == null || friendlyName.isBlank()) {
            return null;
        }
        String normalized = Normalizer.normalize(friendlyName, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "") // Remove acentos
                .toLowerCase()                                      // Converte para minúsculas
                .replaceAll("[^a-z0-9\\s-]", "")                  // Remove caracteres especiais (exceto espaço e hífen)
                .replaceAll("[\\s-]+", "_")                       // Troca espaços e hífens por underscore
                .trim();                                            // Remove espaços no início/fim

        // Remove underscores no início ou fim, se houver
        if (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
