package com.example.bbcinema.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.net.URISyntaxException;

public class PosterUrlValidator implements ConstraintValidator<PosterUrl, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        if (value.matches("/demo/posters/[a-z0-9-]+\\.svg")) return true;
        if (value.matches("/uploads/posters/[a-f0-9]{32}\\.(?:jpg|png)")) return true;
        try {
            URI uri = new URI(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && !uri.getHost().isBlank() && uri.getRawUserInfo() == null
                    && uri.getPort() >= -1 && uri.getPort() <= 65535;
        } catch (URISyntaxException ex) { return false; }
    }
}
