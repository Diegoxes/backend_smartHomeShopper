package com.smarthome.service;

import com.smarthome.entity.AppModule;
import com.smarthome.entity.Role;
import com.smarthome.entity.RoleModule;
import com.smarthome.repository.AppModuleRepository;
import com.smarthome.repository.RoleModuleRepository;
import com.smarthome.repository.RoleRepository;
import com.smarthome.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RbacSeedService {

    private final RoleRepository roleRepository;
    private final AppModuleRepository moduleRepository;
    private final RoleModuleRepository roleModuleRepository;
    private final UserRepository userRepository;

    @Transactional
    public void ensureSeeded() {
        if (roleRepository.count() == 0) {
            seedRolesAndModules();
        }
        Role member = roleRepository.findByName("MEMBER")
                .orElseThrow(() -> new IllegalStateException("Rol MEMBER no encontrado; revisa el seed RBAC."));
        int fixed = userRepository.updateRoleIdWhereNull(member.getId());
        if (fixed > 0) {
            log.info("Usuarios sin role_id actualizados a MEMBER: {}", fixed);
        }
    }

    private void seedRolesAndModules() {
        Role owner = roleRepository.save(Role.builder().name("PLATFORM_OWNER").build());
        Role manager = roleRepository.save(Role.builder().name("MANAGER").build());
        Role member = roleRepository.save(Role.builder().name("MEMBER").build());
        Role viewer = roleRepository.save(Role.builder().name("VIEWER").build());

        AppModule inv = moduleRepository.save(
                AppModule.builder().name("Inventario").key("INVENTORY").build());
        AppModule pur = moduleRepository.save(
                AppModule.builder().name("Compras").key("PURCHASES").build());
        AppModule rep = moduleRepository.save(
                AppModule.builder().name("Informes").key("REPORTS").build());
        AppModule usr = moduleRepository.save(
                AppModule.builder().name("Usuarios").key("USERS").build());

        // PLATFORM_OWNER: CRUD en todos los módulos
        for (AppModule m : new AppModule[] { inv, pur, rep, usr }) {
            grant(owner, m, true, true, true, true);
        }
        // MANAGER: CRUD salvo eliminar en USERS
        grant(manager, inv, true, true, true, true);
        grant(manager, pur, true, true, true, true);
        grant(manager, rep, true, true, true, true);
        grant(manager, usr, true, true, true, false);
        // MEMBER: inventario completo; resto solo lectura
        grant(member, inv, true, true, true, true);
        grant(member, pur, false, true, false, false);
        grant(member, rep, false, true, false, false);
        grant(member, usr, false, true, false, false);
        // VIEWER: solo lectura
        for (AppModule m : new AppModule[] { inv, pur, rep, usr }) {
            grant(viewer, m, false, true, false, false);
        }

        log.info("Seed RBAC: roles, módulos y role_modules creados.");
    }

    private void grant(Role role, AppModule mod, boolean c, boolean r, boolean u, boolean d) {
        RoleModule rm = new RoleModule();
        rm.setRole(role);
        rm.setModule(mod);
        rm.setCanCreate(c);
        rm.setCanRead(r);
        rm.setCanUpdate(u);
        rm.setCanDelete(d);
        roleModuleRepository.save(rm);
    }
}
