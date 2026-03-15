package com.example.examplemod.provider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Downloads raw bytes from a URL — used to fetch GLB files after generation.
 * Shared by all provider implementations so each one doesn't need its own downloader.
 */
public class ModelDownloader {

    private final HttpClient http;

    public ModelDownloader() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Downloads the content at the given URL as a byte array.
     *
     * @param url the URL to download
     * @return raw bytes of the response body
     * @throws Exception on HTTP error or network failure
     */
    public byte[] download(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();

        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to download file: HTTP " + response.statusCode() + " from " + url);
        }

        return response.body();
    }
}
