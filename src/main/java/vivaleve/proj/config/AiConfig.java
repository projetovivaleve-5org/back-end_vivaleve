package vivaleve.proj.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    /** RestClient do Groq, com timeout de conexão e de leitura. */
    @Bean
    public RestClient groqRestClient(AiProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.timeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.timeout());

        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }
}
