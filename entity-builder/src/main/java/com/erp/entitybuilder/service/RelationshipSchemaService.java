package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.EntityRelationship;
import com.erp.entitybuilder.repository.EntityRelationshipRepository;
import com.erp.entitybuilder.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class RelationshipSchemaService {

    private final EntityRelationshipRepository relationshipRepository;

    public RelationshipSchemaService(EntityRelationshipRepository relationshipRepository) {
        this.relationshipRepository = relationshipRepository;
    }

    @Transactional
    public EntityRelationship create(UUID tenantId, String name, String slug, String cardinality,
                                       UUID fromEntityId, UUID toEntityId, String fromFieldSlug, String toFieldSlug) {
        if (relationshipRepository.findByTenantIdAndSlug(tenantId, slug).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "Relationship slug already exists", Map.of("slug", slug));
        }
        EntityRelationship r = new EntityRelationship();
        r.setTenantId(tenantId);
        r.setName(name);
        r.setSlug(slug);
        r.setCardinality(cardinality);
        r.setFromEntityId(fromEntityId);
        r.setToEntityId(toEntityId);
        r.setFromFieldSlug(fromFieldSlug);
        r.setToFieldSlug(toFieldSlug);
        return relationshipRepository.save(r);
    }

    @Transactional(readOnly = true)
    public List<EntityRelationship> list(UUID tenantId) {
        return relationshipRepository.findByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public EntityRelationship get(UUID tenantId, UUID relationshipId) {
        return relationshipRepository.findById(relationshipId)
                .filter(r -> tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Relationship not found"));
    }

    @Transactional
    public EntityRelationship update(UUID tenantId, UUID relationshipId, Optional<String> name, Optional<String> slug, Optional<String> cardinality,
                                      Optional<String> fromFieldSlug, Optional<String> toFieldSlug) {
        EntityRelationship r = get(tenantId, relationshipId);

        name.filter(v -> v != null && !v.isBlank()).ifPresent(r::setName);
        slug.filter(v -> v != null && !v.isBlank()).ifPresent(newSlug -> {
            if (!newSlug.equals(r.getSlug()) && relationshipRepository.findByTenantIdAndSlug(tenantId, newSlug).isPresent()) {
                throw new ApiException(HttpStatus.CONFLICT, "conflict", "Relationship slug already exists", Map.of("slug", newSlug));
            }
            r.setSlug(newSlug);
        });
        cardinality.filter(v -> v != null && !v.isBlank()).ifPresent(r::setCardinality);
        fromFieldSlug.ifPresent(r::setFromFieldSlug);
        toFieldSlug.ifPresent(r::setToFieldSlug);
        return relationshipRepository.save(r);
    }

    @Transactional
    public void delete(UUID tenantId, UUID relationshipId) {
        EntityRelationship r = get(tenantId, relationshipId);
        relationshipRepository.delete(r);
    }
}

