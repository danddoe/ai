package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.EntityRecordValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntityRecordValueRepository extends JpaRepository<EntityRecordValue, UUID> {
    Optional<EntityRecordValue> findByRecordIdAndFieldId(UUID recordId, UUID fieldId);
    List<EntityRecordValue> findByRecordId(UUID recordId);
}

