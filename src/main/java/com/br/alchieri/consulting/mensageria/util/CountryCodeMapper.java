package com.br.alchieri.consulting.mensageria.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Classe utilitária para mapear nomes de mercados/países da Meta para seus códigos de telefone.
 * A fonte dos dados deve ser a documentação oficial do WhatsApp Business Platform Pricing.
 */
public final class CountryCodeMapper {

    // Mapa de Nome do Mercado -> Código do País.
    // Usamos um bloco estático para inicializar o mapa de forma mais legível.
    private static final Map<String, String> MARKET_TO_CODE;

    // Mapa de Código do País -> Nome da Região "Rest of..." (para o caminho inverso)
    private static final Map<String, String> CODE_TO_REGION;

    static {
        Map<String, String> marketMap = new HashMap<>();
        // Mercados individuais
        marketMap.put("Argentina", "54");
        marketMap.put("Brazil", "55");
        marketMap.put("Chile", "56");
        marketMap.put("Colombia", "57");
        marketMap.put("Egypt", "20");
        marketMap.put("France", "33");
        marketMap.put("Germany", "49");
        marketMap.put("India", "91");
        marketMap.put("Indonesia", "62");
        marketMap.put("Israel", "972");
        marketMap.put("Italy", "39");
        marketMap.put("Malaysia", "60");
        marketMap.put("Mexico", "52");
        marketMap.put("Netherlands", "31");
        marketMap.put("Nigeria", "234");
        marketMap.put("Pakistan", "92");
        marketMap.put("Peru", "51");
        marketMap.put("Russia", "7");
        marketMap.put("Saudi Arabia", "966");
        marketMap.put("South Africa", "27");
        marketMap.put("Spain", "34");
        marketMap.put("Turkey", "90");
        marketMap.put("United Arab Emirates", "971");
        marketMap.put("United Kingdom", "44");
        // América do Norte (Canadá e EUA compartilham o código "1")
        marketMap.put("Canada", "1");
        marketMap.put("United States", "1");
        // A chave "North America" no CSV de tarifas representa ambos.
        marketMap.put("North America", "1");
        MARKET_TO_CODE = Collections.unmodifiableMap(marketMap);

        Map<String, String> regionMap = new HashMap<>();
        // Mapeamento de códigos de país para as regiões "Rest of..."
        // Esta lista é baseada na documentação da Meta e precisa ser mantida.
        Set.of("213", "244", "229", "267", "226", "257", "237", "235", "242", "291", "251", "241", "220", "233", "245", "225", "254", "266", "231", "218", "261", "265", "223", "222", "212", "258", "264", "227", "250", "221", "232", "252", "211", "249", "268", "255", "228", "216", "256", "260", "263")
                .forEach(code -> regionMap.put(code, "Rest of Africa"));
        
        Set.of("93", "61", "880", "855", "86", "852", "81", "856", "976", "977", "64", "675", "63", "65", "94", "886", "992", "66", "993", "998", "84")
                .forEach(code -> regionMap.put(code, "Rest of Asia Pacific"));

        Set.of("355", "374", "994", "375", "359", "385", "420", "995", "30", "36", "371", "370", "373", "389", "48", "40", "381", "421", "386", "380")
                .forEach(code -> regionMap.put(code, "Rest of Central & Eastern Europe"));

        Set.of("43", "32", "45", "358", "353", "47", "351", "46", "41")
                .forEach(code -> regionMap.put(code, "Rest of Western Europe"));

        Set.of("591", "506", "1", "593", "503", "502", "509", "504", "505", "507", "595", "598", "58")
                .forEach(code -> regionMap.put(code, "Rest of Latin America")); // Nota: "1" é ambíguo, mas a Meta o inclui aqui para Rep. Dominicana, Jamaica, Porto Rico.

        Set.of("973", "964", "962", "965", "961", "968", "974", "967")
                .forEach(code -> regionMap.put(code, "Rest of Middle East"));

        CODE_TO_REGION = Collections.unmodifiableMap(regionMap);
    }

    /**
     * Construtor privado para impedir a instanciação da classe utilitária.
     */
    private CountryCodeMapper() {}

    /**
     * Obtém o código do país para um nome de mercado/país específico.
     * @param marketName O nome do mercado (ex: "Brazil", "North America").
     * @return A string do código do país (ex: "55", "1") ou null se não for um mercado individual mapeado.
     */
    public static String getCode(String marketName) {
        if (marketName == null) {
            return null;
        }
        return MARKET_TO_CODE.get(marketName);
    }

    /**
     * Determina o "Mercado" ou "Região de Tarifa" para um determinado código de país.
     * Usado para encontrar a linha correta na tabela de tarifas.
     *
     * @param countryCode O código do país (ex: "55", "44", "213").
     * @return O nome do mercado/região (ex: "Brazil", "United Kingdom", "Rest of Africa") ou "Other" como fallback.
     */
    public static String getMarketOrRegion(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return "Other";
        }

        // 1. Tenta encontrar um mapeamento direto de código para mercado individual
        // (Isso requer um mapa reverso, que podemos criar se necessário, mas a lógica abaixo é mais flexível)
        for (Map.Entry<String, String> entry : MARKET_TO_CODE.entrySet()) {
            if (entry.getValue().equals(countryCode)) {
                String market = entry.getKey();
                // Lida com a ambiguidade do código "1"
                if ("1".equals(countryCode) && ("Canada".equals(market) || "United States".equals(market))) {
                    return "North America";
                }
                return market;
            }
        }

        // 2. Se não encontrou um mercado individual, tenta encontrar uma região "Rest of..."
        if (CODE_TO_REGION.containsKey(countryCode)) {
            return CODE_TO_REGION.get(countryCode);
        }

        // 3. Se não encontrou em nenhum lugar, cai no fallback "Other"
        return "Other";
    }
}
