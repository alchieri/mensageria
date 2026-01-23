package com.br.alchieri.consulting.mensageria.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricCountDTO {
    private Long userId;
    private Long count;
}
