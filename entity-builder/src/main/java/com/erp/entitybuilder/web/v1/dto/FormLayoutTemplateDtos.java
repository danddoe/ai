package com.erp.entitybuilder.web.v1.dto;

import java.util.List;
import java.util.Map;

public class FormLayoutTemplateDtos {

    public record FormLayoutTemplateDto(
            String templateKey,
            String title,
            String description,
            List<String> tags,
            Map<String, Object> layout
    ) {}
}
