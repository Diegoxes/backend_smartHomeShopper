package com.smarthome.service;

import com.smarthome.security.SessionPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class OrganizationContextService {

    public SessionPrincipal requireSession() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getDetails() == null || !(auth.getDetails() instanceof SessionPrincipal sp)) {
            throw new AccessDeniedException("No autenticado");
        }
        return sp;
    }

    public String requireUserId() {
        return requireSession().userId();
    }

    public String requireOrgId() {
        String orgId = requireSession().orgId();
        if (orgId == null || orgId.isBlank()) {
            throw new AccessDeniedException("Completa el onboarding de tu negocio para continuar");
        }
        return orgId;
    }

    public boolean isPlatformOwner() {
        try {
            return requireSession().isPlatformOwner();
        } catch (Exception e) {
            return false;
        }
    }
}
