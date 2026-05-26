package com.smarthome.repository;

import com.smarthome.entity.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, String> {

    Optional<OrganizationMember> findByUserId(String userId);

    @Query("SELECT m FROM OrganizationMember m JOIN FETCH m.organization JOIN FETCH m.user WHERE m.organization.id = :orgId ORDER BY m.user.name")
    List<OrganizationMember> findAllByOrganizationId(@Param("orgId") String orgId);

    long countByOrganizationId(String organizationId);

    long countByOrganizationIdAndOrgRole(String organizationId, OrganizationMember.OrgRole orgRole);

    @Query("SELECT m FROM OrganizationMember m JOIN FETCH m.user JOIN FETCH m.organization WHERE m.id = :id AND m.organization.id = :orgId")
    Optional<OrganizationMember> findByIdAndOrganizationId(@Param("id") String id, @Param("orgId") String orgId);

    @Query("SELECT m FROM OrganizationMember m JOIN FETCH m.user u JOIN FETCH m.organization WHERE u.whatsappNumber = :phone")
    Optional<OrganizationMember> findByUserWhatsappNumber(@Param("phone") String phone);
}
