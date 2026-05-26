package com.smarthome.service;

import com.smarthome.entity.Organization;
import com.smarthome.entity.OrganizationMember;
import com.smarthome.entity.OrganizationSettings;
import com.smarthome.entity.Product;
import com.smarthome.entity.Supplier;
import com.smarthome.entity.User;
import com.smarthome.entity.Warehouse;
import com.smarthome.repository.OrganizationMemberRepository;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.repository.OrganizationSettingsRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.repository.RoleRepository;
import com.smarthome.repository.SupplierRepository;
import com.smarthome.repository.UserRepository;
import com.smarthome.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class OrganizationMigrationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final RoleRepository roleRepository;

    @Transactional
    public void migrateIfNeeded() {
        roleRepository.findByName("OWNER").ifPresent(r -> {
            r.setName("PLATFORM_OWNER");
            roleRepository.save(r);
            log.info("Rol OWNER renombrado a PLATFORM_OWNER");
        });

        List<User> users = userRepository.findAll();
        for (User user : users) {
            if (user.getRole() != null && "PLATFORM_OWNER".equals(user.getRole().getName())) {
                continue;
            }
            if (memberRepository.findByUserId(user.getId()).isPresent()) {
                continue;
            }
            Organization org = organizationRepository.save(Organization.builder()
                    .name(defaultOrgName(user))
                    .industry("General")
                    .currency("MXN")
                    .country("MX")
                    .timezone("America/Mexico_City")
                .maxMembers(20)
                .status(Organization.Status.ACTIVE)
                .build());

            OrganizationSettings settings = OrganizationSettings.builder()
                    .organization(org)
                    .expiryAlertDays(7)
                    .predictionHorizonDays(30)
                    .build();
            settingsRepository.save(settings);
            org.setSettings(settings);

            memberRepository.save(OrganizationMember.builder()
                    .organization(org)
                    .user(user)
                    .orgRole(OrganizationMember.OrgRole.MANAGER)
                    .build());

            warehouseRepository.save(Warehouse.builder()
                    .organization(org)
                    .name("Principal")
                    .isDefault(true)
                    .build());

            List<Product> products = productRepository.findAll().stream()
                    .filter(p -> p.getUser() != null && p.getUser().getId().equals(user.getId()))
                    .toList();
            for (Product p : products) {
                p.setOrganization(org);
                productRepository.save(p);
            }

            List<Supplier> suppliers = supplierRepository.findAll().stream()
                    .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                    .toList();
            for (Supplier s : suppliers) {
                s.setOrganization(org);
                supplierRepository.save(s);
            }

            log.info("Migrado usuario {} → organización {}", user.getEmail(), org.getName());
        }
    }

    private static String defaultOrgName(User user) {
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName() + " — Negocio";
        }
        return "Mi negocio";
    }
}
