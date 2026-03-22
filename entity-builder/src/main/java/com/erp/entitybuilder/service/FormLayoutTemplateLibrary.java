package com.erp.entitybuilder.service;

import com.erp.entitybuilder.web.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FormLayoutTemplateLibrary {

    private static final String BASE = "classpath:form-layout-library/";

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    private List<FormLayoutTemplateIndexItem> index = List.of();
    private final Map<String, String> layoutCache = new ConcurrentHashMap<>();

    public FormLayoutTemplateLibrary(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    void loadIndex() {
        Resource indexResource = resourceLoader.getResource(BASE + "index.json");
        if (!indexResource.exists()) {
            index = List.of();
            return;
        }
        try {
            index = objectMapper.readValue(indexResource.getInputStream(), new TypeReference<List<FormLayoutTemplateIndexItem>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load form-layout-library/index.json", e);
        }
        if (index == null) {
            index = List.of();
        }
    }

    public List<FormLayoutTemplateIndexItem> getIndex() {
        return Collections.unmodifiableList(index);
    }

    public String requireLayoutJson(String templateKey) {
        FormLayoutTemplateIndexItem row = index.stream()
                .filter(e -> templateKey.equals(e.getTemplateKey()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Unknown form layout template", Map.of("templateKey", templateKey)));

        String rawFile = row.getLayoutFile();
        if (rawFile == null || rawFile.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "misconfigured", "Template has no layoutFile", Map.of("templateKey", templateKey));
        }
        String file = rawFile.trim();
        if (file.contains("..")) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "misconfigured", "Invalid layoutFile path", Map.of("templateKey", templateKey));
        }

        return layoutCache.computeIfAbsent(templateKey, k -> loadLayoutFile(file));
    }

    private String loadLayoutFile(String layoutFile) {
        String path = layoutFile.startsWith("classpath:") ? layoutFile : BASE + layoutFile;
        Resource resource = resourceLoader.getResource(path);
        if (!resource.exists()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "misconfigured", "Template layout file not found", Map.of("layoutFile", layoutFile));
        }
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "misconfigured", "Could not read template layout file", Map.of("layoutFile", layoutFile));
        }
    }

}
