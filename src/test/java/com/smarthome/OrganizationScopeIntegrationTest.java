package com.smarthome;

import com.smarthome.entity.Organization;
import com.smarthome.entity.OrganizationMember;
import com.smarthome.entity.Product;
import com.smarthome.entity.User;
import com.smarthome.repository.OrganizationMemberRepository;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.repository.ProductRepository;
import com.smarthome.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class OrganizationScopeIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired OrganizationMemberRepository memberRepository;
    @Autowired ProductRepository productRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void productsAreScopedByOrganization() {
        Organization orgA = organizationRepository.save(Organization.builder().name("Org A").build());
        Organization orgB = organizationRepository.save(Organization.builder().name("Org B").build());

        User userA = userRepository.save(User.builder()
                .email("a@test.com").password(passwordEncoder.encode("secret")).name("A").build());

        memberRepository.save(OrganizationMember.builder()
                .organization(orgA).user(userA).orgRole(OrganizationMember.OrgRole.MANAGER).build());

        Product pA = productRepository.save(Product.builder()
                .organization(orgA).user(userA).sku("SKU-A").name("Prod A")
                .quantity(1.0).minQuantity(1.0).unit(Product.UnitType.UNIT).build());

        assertTrue(productRepository.findByIdAndOrganizationId(pA.getId(), orgA.getId()).isPresent());
        assertTrue(productRepository.findByIdAndOrganizationId(pA.getId(), orgB.getId()).isEmpty());
    }
}
