package com.example.bbcinema.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;

/** Invoked by Main only, after the server and application runners have finished starting. */
public final class BrowserLauncher {
    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    private BrowserLauncher() {}

    public static void openLoginPage(ConfigurableApplicationContext context) {
        openLoginPage(context, BrowserLauncher::launchDefaultBrowser);
    }

    static void openLoginPage(ConfigurableApplicationContext context, Consumer<URI> browserOpener) {
        if (!context.getEnvironment().getProperty("bbcinema.open-browser", Boolean.class, true)
                || !(context instanceof ServletWebServerApplicationContext webContext)
                || webContext.getWebServer() == null || webContext.getWebServer().getPort() <= 0) {
            return;
        }
        String contextPath = webContext.getServletContext() == null ? "" : webContext.getServletContext().getContextPath();
        if (contextPath == null || contextPath.equals("/")) contextPath = "";
        if (contextPath.endsWith("/")) contextPath = contextPath.substring(0, contextPath.length() - 1);
        boolean ssl = context.getEnvironment().getProperty("server.ssl.enabled", Boolean.class,
                context.getEnvironment().containsProperty("server.ssl.bundle"));
        URI login;
        try {
            login = new URI(ssl ? "https" : "http", null, "localhost", webContext.getWebServer().getPort(),
                    contextPath + "/login", null, null);
        } catch (URISyntaxException ex) {
            log.warn("Could not construct the Bbcinema login URL", ex);
            return;
        }
        log.info("Bbcinema login: {}", login);
        try {
            browserOpener.accept(login);
        } catch (RuntimeException ex) {
            // Opening a desktop browser must never prevent the cinema server from running.
            log.warn("Could not open the browser automatically. Open {} manually. ({})", login, ex.getMessage());
        }
    }

    private static void launchDefaultBrowser(URI login) {
        try {
            String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (operatingSystem.startsWith("windows")) {
                // Spring Boot defaults to AWT headless mode, so Desktop.browse alone cannot work in IntelliJ.
                String windowsRoot = System.getenv("SystemRoot");
                if (windowsRoot == null || windowsRoot.isBlank()) windowsRoot = "C:\\Windows";
                String launcher = Path.of(windowsRoot, "System32", "rundll32.exe").toString();
                new ProcessBuilder(launcher, "url.dll,FileProtocolHandler", login.toASCIIString()).start();
            } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(login);
            } else {
                throw new IllegalStateException("No desktop browser launcher is available");
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
