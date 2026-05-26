package com.smarthome.controller;

import com.smarthome.dto.Dto;
import com.smarthome.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class OrganizationController { 
    private final OrganizationService organizationService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Dto.AuthResponse> onboard(@Valid @RequestBody Dto.OnboardingRequest req) {
        return ResponseEntity.ok(organizationService.onboard(req));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Dto.OrganizationDto me() {
        return organizationService.getMyOrganization();
    }

    @PatchMapping("/me")
    @PreAuthorize("hasAuthority('USERS_UPDATE') or hasRole('ORG_MANAGER')")
    public Dto.OrganizationDto updateMe(@RequestBody Dto.UpdateOrganizationRequest req) {
        return organizationService.updateMyOrganization(req);
    }

    @GetMapping("/me/members")
    @PreAuthorize("hasAuthority('USERS_READ') or hasRole('ORG_MANAGER')")
    public List<Dto.OrgMemberDto> members() {
        return organizationService.listMembers();
    }

    @PostMapping("/me/members")
    @PreAuthorize("hasAuthority('USERS_CREATE') or hasRole('ORG_MANAGER')")
    public ResponseEntity<Dto.OrgMemberDto> addMember(@Valid @RequestBody Dto.CreateOrgMemberRequest req) {
        return ResponseEntity.status(201).body(organizationService.addMember(req));
    }

    @PatchMapping("/me/members/{id}")
    @PreAuthorize("hasAuthority('USERS_UPDATE') or hasRole('ORG_MANAGER')")
    public Dto.OrgMemberDto updateMember(@PathVariable String id, @RequestBody Dto.UpdateOrgMemberRequest req) {
        return organizationService.updateMember(id, req);
    }

    @DeleteMapping("/me/members/{id}")
    @PreAuthorize("hasAuthority('USERS_DELETE') or hasRole('ORG_MANAGER')")
    public ResponseEntity<Void> removeMember(@PathVariable String id) {
        organizationService.removeMember(id);
        return ResponseEntity.noContent().build();
    }
}
