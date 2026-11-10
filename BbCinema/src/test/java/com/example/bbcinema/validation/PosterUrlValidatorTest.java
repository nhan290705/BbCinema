package com.example.bbcinema.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PosterUrlValidatorTest {
    private final PosterUrlValidator validator = new PosterUrlValidator();

    @Test
    void acceptsUploadedDemoAssetsAndHttpImages() {
        assertTrue(validator.isValid(null, null));
        assertTrue(validator.isValid("/demo/posters/summer-train.svg", null));
        assertTrue(validator.isValid("https://example.com/poster.jpg", null));
        assertTrue(validator.isValid("/uploads/posters/0123456789abcdef0123456789abcdef.jpg", null));
        assertTrue(validator.isValid("/uploads/posters/0123456789abcdef0123456789abcdef.png", null));
    }

    @Test
    void rejectsOtherLocalPathsTraversalUnsafeSchemesAndCredentials() {
        for (String value : new String[]{"/admin.html", "/demo/posters/../auth-art.svg", "//example.com/a.svg",
                "/demo/posters/%2e%2e/secret.svg", "javascript:alert(1)", "file:///C:/secret",
                "https://user:pass@example.com/a.jpg", "http://", "https://example.com:99999/a.svg",
                "/uploads/posters/../application.properties", "/uploads/posters/0123456789abcdef0123456789abcdef.svg"}) {
            assertFalse(validator.isValid(value, null), value);
        }
    }
}
