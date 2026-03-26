package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.EntityStatusLabel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntityStatusLabelRepository extends JpaRepository<EntityStatusLabel, UUID> {

    List<EntityStatusLabel> findByEntityStatusId(UUID entityStatusId);

    Optional<EntityStatusLabel> findByEntityStatusIdAndLocale(UUID entityStatusId, String locale);

    void deleteByEntityStatusIdAndLocale(UUID entityStatusId, String locale);
}
