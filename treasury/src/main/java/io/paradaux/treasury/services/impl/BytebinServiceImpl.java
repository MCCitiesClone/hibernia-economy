package io.paradaux.treasury.services.impl;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import io.paradaux.treasury.model.config.BytebinConfiguration;
import io.paradaux.treasury.services.BytebinService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

@Slf4j
public class BytebinServiceImpl implements BytebinService {

    private final BytebinConfiguration config;
    private final HttpClient httpClient;

    @Inject
    public BytebinServiceImpl(BytebinConfiguration config) {
        this.config = config;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public String upload(String content, String contentType) {
        try {
            byte[] compressed = gzip(content.getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getPostUrl()))
                    .header("Content-Type", contentType)
                    .header("Content-Encoding", "gzip")
                    .header("User-Agent", "Treasury-Plugin/1.0")
                    .header("Bytebin-Max-Reads", "1")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(compressed))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Bytebin upload returned status {} for {}", response.statusCode(), config.getPostUrl());
                throw new IllegalStateException("Bytebin upload failed with status " + response.statusCode());
            }

            String url = config.getBaseUrl() + extractKey(response.body());
            log.debug("Bytebin upload OK ({} bytes compressed) -> {}", compressed.length, url);
            return url;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Bytebin upload failed: {}", e.getMessage());
            throw new IllegalStateException("Failed to upload content to Bytebin", e);
        }
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
        }
        return baos.toByteArray();
    }

    private static String extractKey(String responseBody) {
        int keyStart = responseBody.indexOf("\"key\"");
        if (keyStart == -1) {
            throw new IllegalStateException("Bytebin response did not contain a key");
        }
        int valueStart = responseBody.indexOf('"', responseBody.indexOf(':', keyStart) + 1) + 1;
        int valueEnd   = responseBody.indexOf('"', valueStart);
        return responseBody.substring(valueStart, valueEnd);
    }
}
