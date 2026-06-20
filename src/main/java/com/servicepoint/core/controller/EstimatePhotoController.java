package com.servicepoint.core.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/estimate-photos")
@CrossOrigin("*")
public class EstimatePhotoController {

    private final Path uploadDir = Paths.get("uploads/estimate-photos").toAbsolutePath().normalize();

    @Value("${app.server-base-url:${SERVER_BASE_URL:http://localhost:8080}}")
    private String serverBaseUrl;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<String>> upload(
            @RequestParam("files") List<MultipartFile> files) throws IOException {
        Files.createDirectories(uploadDir);
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String original = file.getOriginalFilename();
            String ext = (original != null && original.contains("."))
                    ? original.substring(original.lastIndexOf(".")) : "";
            String filename = UUID.randomUUID() + ext;
            file.transferTo(uploadDir.resolve(filename));
            String base = serverBaseUrl.endsWith("/")
                    ? serverBaseUrl.substring(0, serverBaseUrl.length() - 1) : serverBaseUrl;
            urls.add(base + "/api/estimate-photos/" + filename);
        }
        return ResponseEntity.ok(urls);
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> serve(@PathVariable String filename) throws IOException {
        Path filePath = uploadDir.resolve(filename).normalize();
        if (!filePath.startsWith(uploadDir)) {
            return ResponseEntity.badRequest().build();
        }
        Resource resource = new UrlResource(filePath.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) contentType = "application/octet-stream";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
}
