package com.erp.globalsearch.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    @Qualifier("iamWebClient")
    public WebClient iamWebClient(GlobalSearchProperties props) {
        return baseBuilder(props.getUpstreamTimeoutMs())
                .baseUrl(props.getIamBaseUrl())
                .build();
    }

    @Bean
    @Qualifier("entityBuilderWebClient")
    public WebClient entityBuilderWebClient(GlobalSearchProperties props) {
        return baseBuilder(props.getUpstreamTimeoutMs())
                .baseUrl(props.getEntityBuilderBaseUrl())
                .build();
    }

    private static WebClient.Builder baseBuilder(long timeoutMs) {
        HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofMillis(timeoutMs));
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
