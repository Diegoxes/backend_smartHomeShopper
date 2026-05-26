-- PLATFORM_OWNER administra la plataforma; no opera inventario/compras de tenants.

DELETE FROM organization_members om
USING users u
JOIN roles r ON u.role_id = r.id
WHERE om.user_id = u.id
  AND r.name = 'PLATFORM_OWNER';

DELETE FROM role_modules rm
USING roles r
WHERE rm.role_id = r.id
  AND r.name = 'PLATFORM_OWNER';
