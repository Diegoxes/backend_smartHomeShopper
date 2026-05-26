package com.smarthome.repository;

import com.smarthome.entity.OrganizationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationSettingsRepository extends JpaRepository<OrganizationSettings, String> {

    Optional<OrganizationSettings> findByOrganizationId(String organizationId);
}
