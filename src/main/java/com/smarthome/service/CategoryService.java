package com.smarthome.service;

import com.smarthome.entity.Category;
import com.smarthome.entity.Organization;
import com.smarthome.repository.CategoryRepository;
import com.smarthome.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private record DefaultCategory(String name, String description, String colorHex) {}

    private static final List<DefaultCategory> DEFAULT_CATEGORIES = List.of(
            new DefaultCategory("General", "Productos generales", "#6B7280"),
            new DefaultCategory("Alimentos", "Productos alimenticios", "#10B981"),
            new DefaultCategory("Bebidas", "Bebidas y líquidos", "#3B82F6"),
            new DefaultCategory("Limpieza", "Productos de limpieza", "#8B5CF6"),
            new DefaultCategory("Electrónica", "Dispositivos y componentes electrónicos", "#F59E0B"),
            new DefaultCategory("Herramientas", "Herramientas y equipos", "#EF4444"),
            new DefaultCategory("Oficina", "Suministros de oficina", "#6366F1"),
            new DefaultCategory("Otros", "Otros productos", "#9CA3AF")
    );

    private final CategoryRepository categoryRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public List<Category> getAllByOrganization(String organizationId) {
        return categoryRepository.findByOrganizationIdOrderByNameAsc(organizationId);
    }

    @Transactional
    public Category create(String organizationId, String name, String description, String colorHex) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organización no encontrada"));

        if (categoryRepository.existsByOrganizationIdAndName(organizationId, name)) {
            throw new IllegalArgumentException("Ya existe una categoría con ese nombre en esta organización");
        }

        Category category = Category.builder()
                .organization(org)
                .name(name)
                .description(description)
                .colorHex(colorHex)
                .build();

        return categoryRepository.save(category);
    }

    @Transactional
    public void delete(String categoryId, String organizationId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));

        if (!category.getOrganization().getId().equals(organizationId)) {
            throw new IllegalArgumentException("No tienes permiso para eliminar esta categoría");
        }

        categoryRepository.delete(category);
    }

    @Transactional
    public void seedDefaultsIfEmpty(String organizationId) {
        if (!categoryRepository.findByOrganizationIdOrderByNameAsc(organizationId).isEmpty()) {
            return;
        }
        for (DefaultCategory def : DEFAULT_CATEGORIES) {
            if (!categoryRepository.existsByOrganizationIdAndName(organizationId, def.name())) {
                create(organizationId, def.name(), def.description(), def.colorHex());
            }
        }
    }
}
