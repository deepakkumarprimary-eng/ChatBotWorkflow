package com.xpressbees.chatbot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI chatbotOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Chatbot Workflow Engine API")
                        .version("1.0.0")
                        .description("REST API for managing chatbot workflows and API configurations"));
    }
}
