package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.EntityFieldLabel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntityFieldLabelRepository extends JpaRepository<EntityFieldLabel, UUID> {

    List<EntityFieldLabel> findByEntityFieldIdIn(Collection<UUID> fieldIds);

    Optional<EntityFieldLabel> findByEntityFieldIdAndLocale(UUID entityFieldId, String locale);

    void deleteByEntityFieldIdAndLocale(UUID entityFieldId, String locale);
}
