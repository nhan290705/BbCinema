package com.example.bbcinema.config;

import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrowserLauncherTest {
    private ServletWebServerApplicationContext webContext(MockEnvironment environment, int port, String contextPath) {
        ServletWebServerApplicationContext context = mock(ServletWebServerApplicationContext.class);
        WebServer server = mock(WebServer.class);
        ServletContext servlet = mock(ServletContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        when(context.getWebServer()).thenReturn(server);
        when(context.getServletContext()).thenReturn(servlet);
        when(server.getPort()).thenReturn(port);
        when(servlet.getContextPath()).thenReturn(contextPath);
        return context;
    }

    @Test
    void opensLoginUsingActualBoundPortRatherThanConfiguredPort() {
        MockEnvironment environment = new MockEnvironment().withProperty("server.port", "0");
        var context = webContext(environment, 8087, "");
        List<URI> opened = new ArrayList<>();
        BrowserLauncher.openLoginPage(context, opened::add);
        assertEquals(List.of(URI.create("http://localhost:8087/login")), opened);
    }

    @Test
    void respectsHttpsAndServletContextPath() {
        MockEnvironment environment = new MockEnvironment().withProperty("server.ssl.enabled", "true");
        var context = webContext(environment, 8443, "/cinema");
        List<URI> opened = new ArrayList<>();
        BrowserLauncher.openLoginPage(context, opened::add);
        assertEquals(List.of(URI.create("https://localhost:8443/cinema/login")), opened);
    }

    @Test
    void disabledFlagPreventsAnyBrowserOpening() {
        MockEnvironment environment = new MockEnvironment().withProperty("bbcinema.open-browser", "false");
        var context = webContext(environment, 8080, "");
        List<URI> opened = new ArrayList<>();
        BrowserLauncher.openLoginPage(context, opened::add);
        assertTrue(opened.isEmpty());
    }

    @Test
    void nonServerContextsSuchAsOrdinaryTestsDoNotOpenBrowser() {
        List<URI> opened = new ArrayList<>();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            BrowserLauncher.openLoginPage(context, opened::add);
        }
        assertTrue(opened.isEmpty());
    }

    @Test
    void serverMustHaveStartedBeforeBrowserCanOpen() {
        List<URI> opened = new ArrayList<>();
        var context = webContext(new MockEnvironment(), 0, "");
        BrowserLauncher.openLoginPage(context, opened::add);
        when(context.getWebServer()).thenReturn(null);
        BrowserLauncher.openLoginPage(context, opened::add);
        assertTrue(opened.isEmpty());
    }

    @Test
    void browserFailureDoesNotFailApplicationStartup() {
        var context = webContext(new MockEnvironment(), 8080, "");
        assertDoesNotThrow(() -> BrowserLauncher.openLoginPage(context, uri -> {
            throw new IllegalStateException("No desktop browser is available");
        }));
    }
}
