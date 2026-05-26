package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.*;
import com.smarthome.repository.*;
import com.smarthome.security.SessionPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserPermissionService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<GrantedAuthority> loadAuthorities(SessionPrincipal session) {
        List<GrantedAuthority> out = new ArrayList<>();

        if (session.isPlatformOwner()) {
            out.add(new SimpleGrantedAuthority("ROLE_PLATFORM_OWNER"));
            roleRepository.findByNameWithRoleModules("PLATFORM_OWNER")
                    .ifPresent(r -> appendModuleAuthorities(out, r));
        }

        if (session.orgRole() != null) {
            out.add(new SimpleGrantedAuthority("ROLE_ORG_" + session.orgRole()));
            roleRepository.findByNameWithRoleModules(session.orgRole())
                    .ifPresent(r -> appendModuleAuthorities(out, r));
        }

        if (out.isEmpty()) {
            return userRepository.findByIdWithRbac(session.userId())
                    .map(u -> {
                        List<GrantedAuthority> legacy = new ArrayList<>();
                        if (u.getRole() != null) {
                            legacy.add(new SimpleGrantedAuthority("ROLE_" + u.getRole().getName()));
                            appendModuleAuthorities(legacy, u.getRole());
                        }
                        return legacy;
                    })
                    .orElse(List.of());
        }
        return out;
    }

    private void appendModuleAuthorities(List<GrantedAuthority> out, Role role) {
        if (role.getRoleModules() == null) return;
        for (RoleModule rm : role.getRoleModules()) {
            if (rm.getModule() == null) continue;
            String key = rm.getModule().getKey();
            if (rm.isCanRead()) out.add(new SimpleGrantedAuthority(key + "_READ"));
            if (rm.isCanCreate()) out.add(new SimpleGrantedAuthority(key + "_CREATE"));
            if (rm.isCanUpdate()) out.add(new SimpleGrantedAuthority(key + "_UPDATE"));
            if (rm.isCanDelete()) out.add(new SimpleGrantedAuthority(key + "_DELETE"));
        }
    }

    @Transactional(readOnly = true)
    public List<Dto.ModulePermissionDto> modulePermissionsForSession(SessionPrincipal session) {
        String roleName = session.isPlatformOwner() ? "PLATFORM_OWNER" : session.orgRole();
        if (roleName == null) {
            return userRepository.findByIdWithRbac(session.userId())
                    .map(this::modulePermissionsFromUser)
                    .orElse(List.of());
        }
        return roleRepository.findByNameWithRoleModules(roleName)
                .map(this::modulePermissionsFromRole)
                .orElse(List.of());
    }

    public List<Dto.ModulePermissionDto> modulePermissionsFromUser(User user) {
        if (user.getRole() == null) return List.of();
        return modulePermissionsFromRole(user.getRole());
    }

    private List<Dto.ModulePermissionDto> modulePermissionsFromRole(Role role) {
        if (role.getRoleModules() == null) return List.of();
        List<Dto.ModulePermissionDto> out = new ArrayList<>();
        for (RoleModule rm : role.getRoleModules()) {
            if (rm.getModule() == null) continue;
            out.add(Dto.ModulePermissionDto.builder()
                    .key(rm.getModule().getKey())
                    .canCreate(rm.isCanCreate())
                    .canRead(rm.isCanRead())
                    .canUpdate(rm.isCanUpdate())
                    .canDelete(rm.isCanDelete())
                    .build());
        }
        return out;
    }
}
