package com.smarthome.service;

import com.smarthome.dto.Dto;
import com.smarthome.entity.AppModule;
import com.smarthome.entity.Role;
import com.smarthome.entity.RoleModule;
import com.smarthome.repository.AppModuleRepository;
import com.smarthome.repository.RoleModuleRepository;
import com.smarthome.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminRbacService {

    private final RoleRepository roleRepository;
    private final AppModuleRepository moduleRepository;
    private final RoleModuleRepository roleModuleRepository;

    @Transactional(readOnly = true)
    public List<Dto.AdminRoleDto> listRoles() {
        return roleRepository.findAll(Sort.by("id")).stream()
                .map(r -> Dto.AdminRoleDto.builder().id(r.getId()).name(r.getName()).build())
                .toList();
    }

    @Transactional(readOnly = true)
    public Dto.RbacMatrixResponse getMatrix() {
        List<Role> roles = roleRepository.findAllWithRoleModules();
        List<AppModule> modules = moduleRepository.findAll(Sort.by("id"));

        List<Dto.AdminRoleDto> roleDtos = roles.stream()
                .map(r -> Dto.AdminRoleDto.builder().id(r.getId()).name(r.getName()).build())
                .sorted(Comparator.comparing(Dto.AdminRoleDto::getId))
                .toList();

        List<Dto.AdminModuleDto> modDtos = modules.stream()
                .map(m -> Dto.AdminModuleDto.builder().id(m.getId()).name(m.getName()).key(m.getKey()).build())
                .toList();

        List<Dto.RoleModuleCellDto> cells = new ArrayList<>();
        for (Role r : roles) {
            if (r.getRoleModules() == null) {
                continue;
            }
            for (RoleModule rm : r.getRoleModules()) {
                if (rm.getModule() == null) {
                    continue;
                }
                cells.add(Dto.RoleModuleCellDto.builder()
                        .roleId(r.getId())
                        .moduleId(rm.getModule().getId())
                        .canCreate(rm.isCanCreate())
                        .canRead(rm.isCanRead())
                        .canUpdate(rm.isCanUpdate())
                        .canDelete(rm.isCanDelete())
                        .build());
            }
        }

        return Dto.RbacMatrixResponse.builder()
                .roles(roleDtos)
                .modules(modDtos)
                .permissions(cells)
                .build();
    }

    @Transactional
    public void updatePermissionsBatch(List<Dto.RoleModuleCellDto> cells) {
        for (Dto.RoleModuleCellDto cell : cells) {
            RoleModule rm = roleModuleRepository
                    .findByRole_IdAndModule_Id(cell.getRoleId(), cell.getModuleId())
                    .orElseThrow(() -> new RuntimeException(
                            "Combinación rol/módulo no encontrada: " + cell.getRoleId() + " / " + cell.getModuleId()));
            rm.setCanCreate(cell.isCanCreate());
            rm.setCanRead(cell.isCanRead());
            rm.setCanUpdate(cell.isCanUpdate());
            rm.setCanDelete(cell.isCanDelete());
            roleModuleRepository.save(rm);
        }
    }
}
