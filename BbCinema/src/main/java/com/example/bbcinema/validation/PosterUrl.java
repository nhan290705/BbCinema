package com.example.bbcinema.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PosterUrlValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PosterUrl {
    String message() default "Poster phải là URL HTTP/HTTPS hợp lệ hoặc đường dẫn poster đã tải lên";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
