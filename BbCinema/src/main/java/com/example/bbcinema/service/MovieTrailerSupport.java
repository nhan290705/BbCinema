package com.example.bbcinema.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public final class MovieTrailerSupport {
    private MovieTrailerSupport() {}
    private static final Set<String> YOUTUBE = Set.of("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
            "youtube-nocookie.com", "www.youtube-nocookie.com");

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = new URI(value.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getPort() > 65535) throw invalid();
            String host = uri.getHost().toLowerCase(Locale.ROOT), path = uri.getPath(), id = null;
            if (host.equals("youtu.be") || host.equals("www.youtu.be")) id = path.substring(1);
            else if (YOUTUBE.contains(host)) {
                if (path.equals("/watch") && uri.getRawQuery() != null) {
                    for (String pair : uri.getRawQuery().split("&")) {
                        String[] item = pair.split("=", 2);
                        if (item.length == 2 && item[0].equals("v")) { id = URLDecoder.decode(item[1], StandardCharsets.UTF_8); break; }
                    }
                } else if (path.matches("/(embed|shorts|live)/[^/]+")) id = path.substring(path.lastIndexOf('/') + 1);
            } else return uri.toASCIIString();
            if (id == null || !id.matches("[A-Za-z0-9_-]{11}")) throw invalid();
            return "https://www.youtube.com/watch?v=" + id;
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw invalid(); }
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trailer cần liên kết HTTP/HTTPS hợp lệ; với YouTube hãy dùng liên kết của một video cụ thể");
    }
}
