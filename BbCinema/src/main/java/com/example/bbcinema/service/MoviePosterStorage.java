package com.example.bbcinema.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class MoviePosterStorage {
    public static final long MAX_BYTES = 5 * 1024 * 1024;
    public static final String PUBLIC_PREFIX = "/uploads/posters/";
    public static final String FILE_PATTERN = "[a-f0-9]{32}\\.(?:jpg|png)";
    private static final Logger log = LoggerFactory.getLogger(MoviePosterStorage.class);
    private final Path directory;

    public MoviePosterStorage(@Value("${bbcinema.media.poster-directory:uploads/posters}") String location) {
        Path base = Path.of("").toAbsolutePath().normalize();
        // IntelliJ may launch the BbCinema module from its parent project directory.
        if (!Files.isRegularFile(base.resolve("pom.xml")) && Files.isRegularFile(base.resolve("BbCinema/pom.xml"))) {
            base = base.resolve("BbCinema");
        }
        directory = base.resolve(location).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file) {
        if (file.isEmpty()) throw bad("Ảnh poster đang trống. Hãy chọn ảnh JPG hoặc PNG");
        if (file.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Poster tối đa 5 MB");
        String contentType = file.getContentType();
        if (!"image/jpeg".equalsIgnoreCase(contentType) && !"image/png".equalsIgnoreCase(contentType)) {
            throw bad("Poster chỉ nhận ảnh JPG hoặc PNG");
        }
        Path target = null;
        try {
            // Check the decoded image, not the user supplied extension. Re-encode to remove metadata/trailing data.
            BufferedImage image;
            String format;
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw bad("Tệp đã chọn không phải ảnh JPG/PNG hợp lệ");
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!format.equals("jpeg") && !format.equals("png")) throw bad("Poster chỉ nhận ảnh JPG hoặc PNG");
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > 8000 || height > 8000 || (long) width * height > 20_000_000) {
                        throw bad("Poster tối đa 8.000 pixel mỗi chiều và 20 triệu pixel");
                    }
                    image = reader.read(0);
                } finally { reader.dispose(); }
            }
            Files.createDirectories(directory);
            String filename = UUID.randomUUID().toString().replace("-", "") + (format.equals("jpeg") ? ".jpg" : ".png");
            target = directory.resolve(filename);
            try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                if (!ImageIO.write(image, format, output)) throw new IOException("Image writer unavailable");
            }
            return PUBLIC_PREFIX + filename;
        } catch (ResponseStatusException ex) { throw ex; }
        catch (IOException ex) {
            if (target != null) deletePath(target);
            log.warn("Could not read or store a movie poster", ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đọc/lưu được ảnh poster. Hãy chọn ảnh JPG/PNG hợp lệ và thử lại");
        }
    }

    public Resource load(String filename) {
        if (filename == null || !filename.matches(FILE_PATTERN)) throw missing();
        Path path = directory.resolve(filename).normalize();
        if (!path.getParent().equals(directory) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw missing();
        return new FileSystemResource(path);
    }

    public void deleteCreated(String url) {
        if (url != null && url.startsWith(PUBLIC_PREFIX)) {
            String filename = url.substring(PUBLIC_PREFIX.length());
            if (filename.matches(FILE_PATTERN)) deletePath(directory.resolve(filename));
        }
    }

    private void deletePath(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException ex) { log.warn("Could not remove an uncommitted movie poster: {}", path, ex); }
    }

    private static ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy poster"); }
}
