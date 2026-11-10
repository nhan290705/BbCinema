package com.example.bbcinema.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.*;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class LoginOpenApiConfig {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("Bbcinema API").version("1.0")
            .description("Đăng ký → đăng nhập bằng JSON → gọi /api/users/me và API theo role. Swagger tự gửi CSRF và cookie session. redirectUrl là đường dẫn để frontend điều hướng."));
    }
    @Bean
    public OpenApiCustomizer logoutDocumentation() {
        return api -> api.getPaths().addPathItem("/api/auth/logout", new PathItem().post(
            new Operation().addTagsItem("auth-controller").summary("Đăng xuất phiên hiện tại")
                .responses(new ApiResponses().addApiResponse("204", new ApiResponse().description("Đã đăng xuất"))
                    .addApiResponse("403", new ApiResponse().description("CSRF token không hợp lệ")))));
    }
}
