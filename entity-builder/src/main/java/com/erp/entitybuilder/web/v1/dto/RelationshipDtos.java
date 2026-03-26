package com.erp.entitybuilder.web.v1.dto;

import com.erp.entitybuilder.domain.DefinitionScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class RelationshipDtos {

    public record RelationshipDto(
            UUID id,
            UUID tenantId,
            String name,
            String slug,
            UUID fromEntityId,
            UUID toEntityId,
            String fromFieldSlug,
            String toFieldSlug,
            String cardinality,
            DefinitionScope definitionScope,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public static class CreateRelationshipRequest {
        @NotBlank
        @Size(max = 255)
        private String name;

        @NotBlank
        @Size(max = 100)
        private String slug;

        @NotBlank
        private String cardinality; // one-to-one, one-to-many, many-to-many

        private UUID fromEntityId;
        private UUID toEntityId;
        private String fromFieldSlug;
        private String toFieldSlug;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public String getCardinality() { return cardinality; }
        public void setCardinality(String cardinality) { this.cardinality = cardinality; }
        public UUID getFromEntityId() { return fromEntityId; }
        public void setFromEntityId(UUID fromEntityId) { this.fromEntityId = fromEntityId; }
        public UUID getToEntityId() { return toEntityId; }
        public void setToEntityId(UUID toEntityId) { this.toEntityId = toEntityId; }
        public String getFromFieldSlug() { return fromFieldSlug; }
        public void setFromFieldSlug(String fromFieldSlug) { this.fromFieldSlug = fromFieldSlug; }
        public String getToFieldSlug() { return toFieldSlug; }
        public void setToFieldSlug(String toFieldSlug) { this.toFieldSlug = toFieldSlug; }
    }

    public static class UpdateRelationshipRequest {
        @Size(max = 255)
        private String name;

        @Size(max = 100)
        private String slug;

        @Size(max = 50)
        private String cardinality;

        private String fromFieldSlug;
        private String toFieldSlug;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public String getCardinality() { return cardinality; }
        public void setCardinality(String cardinality) { this.cardinality = cardinality; }
        public String getFromFieldSlug() { return fromFieldSlug; }
        public void setFromFieldSlug(String fromFieldSlug) { this.fromFieldSlug = fromFieldSlug; }
        public String getToFieldSlug() { return toFieldSlug; }
        public void setToFieldSlug(String toFieldSlug) { this.toFieldSlug = toFieldSlug; }
    }
}

