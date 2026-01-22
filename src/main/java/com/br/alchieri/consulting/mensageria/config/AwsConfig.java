package com.br.alchieri.consulting.mensageria.config;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.awspring.cloud.autoconfigure.core.AwsAutoConfiguration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@AutoConfigureAfter(AwsAutoConfiguration.class) // Garante que os beans de credenciais/região sejam criados antes
public class AwsConfig {

    /**
     * Cria um bean S3Presigner que será usado para gerar URLs pré-assinadas.
     * Ele utiliza as credenciais e a região configuradas automaticamente pelo Spring Cloud AWS.
     *
     * @param credentialsProvider O provedor de credenciais padrão do Awspring.
     * @param regionProvider O provedor de região padrão do Awspring.
     * @return uma instância de S3Presigner.
     */
    @Bean
    public S3Presigner s3Presigner(AwsCredentialsProvider credentialsProvider, AwsRegionProvider regionProvider) {
        return S3Presigner.builder()
                .credentialsProvider(credentialsProvider)
                .region(regionProvider.getRegion())
                .build();
    }
}
