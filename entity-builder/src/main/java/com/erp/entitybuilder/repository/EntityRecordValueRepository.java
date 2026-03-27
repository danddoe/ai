package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.EntityRecordValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntityRecordValueRepository extends JpaRepository<EntityRecordValue, UUID> {
    Optional<EntityRecordValue> findByRecordIdAndFieldId(UUID recordId, UUID fieldId);
    List<EntityRecordValue> findByRecordId(UUID recordId);

    @Query("""
            SELECT CASE WHEN COUNT(erv) > 0 THEN true ELSE false END
            FROM EntityRecordValue erv
            WHERE erv.fieldId = :fieldId
              AND (erv.valueText IS NOT NULL
                OR erv.valueNumber IS NOT NULL
                OR erv.valueDate IS NOT NULL
                OR erv.valueBoolean IS NOT NULL
                OR erv.valueReference IS NOT NULL)""")
    boolean existsNonNullValueForField(@Param("fieldId") UUID fieldId);
}

