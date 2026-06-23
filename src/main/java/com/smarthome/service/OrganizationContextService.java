package com.smarthome.service;

import com.smarthome.entity.Organization;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.security.SessionPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrganizationContextService {

    private final OrganizationRepository organizationRepository;

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

    /** Requiere org activa; bloquea operaciones de negocio mientras la org está PENDING o REJECTED. */
    public String requireActiveOrgId() {
        String orgId = requireOrgId();
        if (isPlatformOwner()) return orgId;
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new AccessDeniedException("Organización no encontrada"));
        if (org.getStatus() != Organization.Status.ACTIVE) {
            throw new AccessDeniedException("Tu organización está pendiente de aprobación");
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
