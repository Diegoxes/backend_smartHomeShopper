package com.smarthome.service;

import com.smarthome.entity.Category;
import com.smarthome.entity.Organization;
import com.smarthome.repository.CategoryRepository;
import com.smarthome.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private record DefaultCategory(String name, String description, String colorHex) {}

    /** Categorías de producto genéricas (no confundir con rubro/industry de la organización). */
    private static final List<DefaultCategory> GENERIC_PRODUCT_CATEGORIES = List.of(
            new DefaultCategory("General", "Productos sin clasificación específica", "#6B7280"),
            new DefaultCategory("Alimentos", "Productos alimenticios", "#10B981"),
            new DefaultCategory("Bebidas", "Bebidas y líquidos", "#3B82F6"),
            new DefaultCategory("Limpieza", "Productos de limpieza e higiene", "#8B5CF6"),
            new DefaultCategory("Electrónica", "Dispositivos y componentes electrónicos", "#F59E0B"),
            new DefaultCategory("Herramientas", "Herramientas y equipos", "#EF4444"),
            new DefaultCategory("Oficina", "Suministros de oficina", "#6366F1"),
            new DefaultCategory("Otros", "Otros productos", "#9CA3AF")
    );

    private static final List<DefaultCategory> FERRETERIA_PRODUCT_CATEGORIES = List.of(
            new DefaultCategory("Herramientas", "Herramientas manuales y eléctricas", "#EF4444"),
            new DefaultCategory("Electricidad", "Cableado, focos e instalación eléctrica", "#F59E0B"),
            new DefaultCategory("Plomería", "Tubería, llaves y accesorios", "#3B82F6"),
            new DefaultCategory("Pinturas", "Pinturas, brochas y solventes", "#8B5CF6"),
            new DefaultCategory("Construcción", "Cemento, varilla y materiales de obra", "#78716C"),
            new DefaultCategory("Fijaciones", "Tornillos, clavos y anclajes", "#6B7280"),
            new DefaultCategory("Otros", "Otros productos", "#9CA3AF")
    );

    private static final List<DefaultCategory> FARMACIA_PRODUCT_CATEGORIES = List.of(
            new DefaultCategory("Medicamentos", "Medicinas de venta libre y controladas", "#EF4444"),
            new DefaultCategory("Higiene personal", "Jabones, shampoo y cuidado personal", "#3B82F6"),
            new DefaultCategory("Primeros auxilios", "Curitas, antisépticos y vendajes", "#10B981"),
            new DefaultCategory("Suplementos", "Vitaminas y suplementos alimenticios", "#F59E0B"),
            new DefaultCategory("Cosmética", "Maquillaje y cuidado de la piel", "#EC4899"),
            new DefaultCategory("Otros", "Otros productos", "#9CA3AF")
    );

    private static final List<DefaultCategory> RETAIL_FOOD_PRODUCT_CATEGORIES = List.of(
            new DefaultCategory("Abarrotes", "Productos de despensa", "#F59E0B"),
            new DefaultCategory("Lácteos", "Leche, queso y derivados", "#3B82F6"),
            new DefaultCategory("Bebidas", "Refrescos, jugos y agua", "#06B6D4"),
            new DefaultCategory("Snacks", "Botanas y golosinas", "#EC4899"),
            new DefaultCategory("Limpieza", "Productos de limpieza del hogar", "#8B5CF6"),
            new DefaultCategory("Cuidado personal", "Higiene y cuidado corporal", "#10B981"),
            new DefaultCategory("Otros", "Otros productos", "#9CA3AF")
    );

    private static final List<DefaultCategory> RESTAURANT_PRODUCT_CATEGORIES = List.of(
            new DefaultCategory("Carnes", "Carnes y proteínas", "#EF4444"),
            new DefaultCategory("Verduras", "Verduras y hortalizas", "#10B981"),
            new DefaultCategory("Lácteos", "Leche, queso y mantequilla", "#3B82F6"),
            new DefaultCategory("Abarrotes", "Arroz, pasta y granos", "#F59E0B"),
            new DefaultCategory("Bebidas", "Bebidas para servicio", "#06B6D4"),
            new DefaultCategory("Condimentos", "Especias, salsas y aderezos", "#78716C"),
            new DefaultCategory("Desechables", "Vasos, servilletas y empaques", "#6B7280"),
            new DefaultCategory("Otros", "Otros insumos", "#9CA3AF")
    );

    private static final List<DefaultCategory> OFICINA_PRODUCT_CATEGORIES = List.of(
            new DefaultCategory("Papelería", "Papel, cuadernos y folders", "#6366F1"),
            new DefaultCategory("Tecnología", "Tóner, cables y accesorios", "#3B82F6"),
            new DefaultCategory("Mobiliario", "Sillas, escritorios y estantes", "#78716C"),
            new DefaultCategory("Limpieza", "Productos de limpieza de oficina", "#8B5CF6"),
            new DefaultCategory("Cafetería", "Café, azúcar y consumibles", "#F59E0B"),
            new DefaultCategory("Otros", "Otros suministros", "#9CA3AF")
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
        Organization org = organizationRepository.findById(organizationId).orElse(null);
        String industry = org != null && org.getIndustry() != null
                ? org.getIndustry().trim().toLowerCase(Locale.ROOT)
                : "";
        List<DefaultCategory> defaults = productCategoriesForIndustry(industry);
        for (DefaultCategory def : defaults) {
            if (!categoryRepository.existsByOrganizationIdAndName(organizationId, def.name())) {
                create(organizationId, def.name(), def.description(), def.colorHex());
            }
        }
    }

    private static List<DefaultCategory> productCategoriesForIndustry(String industry) {
        if (matchesAny(industry, "ferreter", "hardware")) {
            return FERRETERIA_PRODUCT_CATEGORIES;
        }
        if (matchesAny(industry, "farmac", "pharm")) {
            return FARMACIA_PRODUCT_CATEGORIES;
        }
        if (matchesAny(industry, "restaur", "cafeter", "food service", "comida")) {
            return RESTAURANT_PRODUCT_CATEGORIES;
        }
        if (matchesAny(industry, "bodega", "abarrot", "minimarket", "super", "tienda", "comercio", "retail")) {
            return RETAIL_FOOD_PRODUCT_CATEGORIES;
        }
        if (matchesAny(industry, "oficina", "office")) {
            return OFICINA_PRODUCT_CATEGORIES;
        }
        return GENERIC_PRODUCT_CATEGORIES;
    }

    private static boolean matchesAny(String industry, String... keywords) {
        if (industry.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (industry.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
