package com.erp.entitybuilder.web.v1;

import com.erp.entitybuilder.service.FormLayoutTemplateIndexItem;
import com.erp.entitybuilder.service.FormLayoutTemplateLibrary;
import com.erp.entitybuilder.web.v1.dto.FormLayoutTemplateDtos;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/form-layout-templates")
public class FormLayoutTemplateController {

    private final FormLayoutTemplateLibrary templateLibrary;
    private final ObjectMapper objectMapper;

    public FormLayoutTemplateController(FormLayoutTemplateLibrary templateLibrary, ObjectMapper objectMapper) {
        this.templateLibrary = templateLibrary;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('entity_builder:schema:read')")
    public List<FormLayoutTemplateDtos.FormLayoutTemplateDto> list(
            @RequestParam(name = "includeLayout", defaultValue = "false") boolean includeLayout
    ) {
        List<FormLayoutTemplateIndexItem> index = templateLibrary.getIndex();
        return index.stream().map(row -> toDto(row, includeLayout)).toList();
    }

    private FormLayoutTemplateDtos.FormLayoutTemplateDto toDto(FormLayoutTemplateIndexItem row, boolean includeLayout) {
        Map<String, Object> layout = null;
        if (includeLayout) {
            try {
                String json = templateLibrary.requireLayoutJson(row.getTemplateKey());
                layout = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                layout = Map.of("error", "could_not_load_layout");
            }
        }
        return new FormLayoutTemplateDtos.FormLayoutTemplateDto(
                row.getTemplateKey(),
                row.getTitle(),
                row.getDescription(),
                row.getTags() != null ? List.copyOf(row.getTags()) : List.of(),
                layout
        );
    }
}
