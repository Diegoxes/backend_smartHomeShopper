package com.smarthome.service;

import com.smarthome.entity.Role;
import com.smarthome.entity.RoleModule;
import com.smarthome.entity.User;
import com.smarthome.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserPermissionService {

    private final UserRepository userRepository;

    public List<GrantedAuthority> loadAuthorities(String userId) {
        return userRepository.findByIdWithRbac(userId)
                .map(this::toAuthorities)
                .orElse(List.of());
    }

    private List<GrantedAuthority> toAuthorities(User user) {
        List<GrantedAuthority> out = new ArrayList<>();
        Role role = user.getRole();
        if (role == null) {
            return out;
        }
        out.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
        List<RoleModule> rms = role.getRoleModules();
        if (rms == null) {
            return out;
        }
        for (RoleModule rm : rms) {
            if (rm.getModule() == null) {
                continue;
            }
            String key = rm.getModule().getKey();
            if (rm.isCanRead()) {
                out.add(new SimpleGrantedAuthority(key + "_READ"));
            }
            if (rm.isCanCreate()) {
                out.add(new SimpleGrantedAuthority(key + "_CREATE"));
            }
            if (rm.isCanUpdate()) {
                out.add(new SimpleGrantedAuthority(key + "_UPDATE"));
            }
            if (rm.isCanDelete()) {
                out.add(new SimpleGrantedAuthority(key + "_DELETE"));
            }
        }
        return out;
    }
}
