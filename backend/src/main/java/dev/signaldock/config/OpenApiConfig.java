package dev.signaldock.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI signalDockOpenApi() {
        String scheme = "apiKey";
        return new OpenAPI()
                .info(new Info()
                        .title("SignalDock API")
                        .version("1.0")
                        .contact(new Contact()
                                .name("Yashwardhan Verma")
                                .url("https://www.linkedin.com/in/yashwardhanv")
                                .email("yashwardhanverma108@gmail.com"))
                        .description("Register callback endpoints, ingest events, and inspect reliable deliveries."))
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .components(new Components().addSecuritySchemes(scheme, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-Key")));
    }
}
