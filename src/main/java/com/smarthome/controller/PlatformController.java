package com.smarthome.controller;

import com.smarthome.dto.Dto;
import com.smarthome.service.PlatformService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/platform")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_OWNER')")
public class PlatformController {

    private final PlatformService platformService;

    @GetMapping("/organizations")
    public List<Dto.PlatformOrganizationRowDto> organizations() {
        return platformService.listOrganizations();
    }

    @GetMapping("/users")
    public List<Dto.PlatformUserRowDto> users() {
        return platformService.listUsers();
    }

    @PatchMapping("/organizations/{id}/max-members")
    public Dto.PlatformOrganizationRowDto maxMembers(
            @PathVariable String id,
            @Valid @RequestBody Dto.MaxMembersRequest req) {
        return platformService.updateMaxMembers(id, req.getMaxMembers());
    }
}
