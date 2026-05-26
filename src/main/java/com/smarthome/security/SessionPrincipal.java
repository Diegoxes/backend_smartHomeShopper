package com.smarthome.security;

/** Contexto de sesión extraído del JWT. */
public record SessionPrincipal(
        String userId,
        String orgId,
        String orgRole,
        String platformRole
) {
    public boolean isPlatformOwner() {
        return "PLATFORM_OWNER".equals(platformRole);
    }

    public boolean isOrgManager() {
        return "MANAGER".equals(orgRole);
    }
}
