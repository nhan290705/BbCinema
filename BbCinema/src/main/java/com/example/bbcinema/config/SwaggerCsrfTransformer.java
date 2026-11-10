package com.example.bbcinema.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class SwaggerCsrfTransformer
        extends SwaggerIndexPageTransformer {

    public SwaggerCsrfTransformer(
            SwaggerUiConfigProperties properties,
            SwaggerUiOAuthProperties oauth,
            SwaggerWelcomeCommon welcome,
            ObjectMapperProvider mapper) {

        super(properties, oauth, welcome, mapper);
    }

    @Override
    public Resource transform(
            HttpServletRequest request,
            Resource resource,
            ResourceTransformerChain chain) throws IOException {

        // Giữ nguyên cách springdoc tạo cấu hình Swagger.
        Resource transformed = super.transform(
                request, resource, chain
        );

        // Chỉ chỉnh file khởi tạo Swagger.
        if (!"swagger-initializer.js".equals(resource.getFilename())) {
            return transformed;
        }

        String script;

        try (var stream = transformed.getInputStream()) {
            script = new String(
                    stream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }

        String interceptor;

        // Đọc file JavaScript đã tạo ở bước 3.
        try (var stream = new ClassPathResource(
                "swagger/auto-csrf.js"
        ).getInputStream()) {

            interceptor = new String(
                    stream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }

        String endpoint = new ObjectMapper().writeValueAsString(
                request.getContextPath() + "/api/auth/csrf"
        );

        interceptor = interceptor.replace(
                "__CSRF_ENDPOINT__",
                endpoint
        );

        String marker = "SwaggerUIBundle({";

        if (!script.contains(marker)) {
            throw new IOException(
                    "Swagger initializer changed; "
                            + "cannot install CSRF interceptor"
            );
        }

        // Chèn hàm tự lấy token vào cấu hình Swagger.
        script = script.replace(
                marker,
                marker
                        + "\nrequestInterceptor: "
                        + interceptor
                        + ",\n"
        );

        return new TransformedResource(
                transformed,
                script.getBytes(StandardCharsets.UTF_8)
        );
    }
}