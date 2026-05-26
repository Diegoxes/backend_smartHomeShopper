package com.smarthome.repository;

import com.smarthome.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganizationRepository extends JpaRepository<Organization, String> {

    List<Organization> findAllByOrderByNameAsc();

    List<Organization> findAllByStatusOrderByCreatedAtAsc(Organization.Status status);
}
