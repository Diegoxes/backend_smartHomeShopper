package com.smarthome.service;

import com.smarthome.entity.Category;
import com.smarthome.repository.CategoryRepository;
import com.smarthome.repository.OrganizationRepository;
import com.smarthome.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepository categoryRepository;
    @Mock OrganizationRepository organizationRepository;
    @InjectMocks CategoryService categoryService;

    @Test
    void create_duplicateName_throws() {
        when(organizationRepository.findById(TestFixtures.ORG_ID)).thenReturn(Optional.of(TestFixtures.organization()));
        when(categoryRepository.existsByOrganizationIdAndName(TestFixtures.ORG_ID, "General")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> categoryService.create(TestFixtures.ORG_ID, "General", "desc", "#fff"));
    }

    @Test
    void create_success() {
        when(organizationRepository.findById(TestFixtures.ORG_ID)).thenReturn(Optional.of(TestFixtures.organization()));
        when(categoryRepository.existsByOrganizationIdAndName(anyString(), anyString())).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category created = categoryService.create(TestFixtures.ORG_ID, "Nueva", "desc", "#000");

        assertEquals("Nueva", created.getName());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void seedDefaultsIfEmpty_seedsForFerreteria() {
        when(categoryRepository.findByOrganizationIdOrderByNameAsc(TestFixtures.ORG_ID)).thenReturn(List.of());
        when(organizationRepository.findById(TestFixtures.ORG_ID)).thenReturn(Optional.of(TestFixtures.organization()));
        when(categoryRepository.existsByOrganizationIdAndName(anyString(), anyString())).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        categoryService.seedDefaultsIfEmpty(TestFixtures.ORG_ID);

        verify(categoryRepository, atLeast(5)).save(any(Category.class));
    }

    @Test
    void seedDefaultsIfEmpty_skipsWhenCategoriesExist() {
        when(categoryRepository.findByOrganizationIdOrderByNameAsc(TestFixtures.ORG_ID))
                .thenReturn(List.of(TestFixtures.category()));

        categoryService.seedDefaultsIfEmpty(TestFixtures.ORG_ID);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void delete_wrongOrg_throws() {
        Category cat = TestFixtures.category();
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(cat));

        assertThrows(IllegalArgumentException.class,
                () -> categoryService.delete("cat-1", "other-org"));
    }
}
