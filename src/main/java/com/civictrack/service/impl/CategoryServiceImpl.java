package com.civictrack.service.impl;

import com.civictrack.dto.CategoryForm;
import com.civictrack.entity.Category;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.ResourceNotFoundException;
import com.civictrack.repository.CategoryRepository;
import com.civictrack.service.CategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findActive() {
        return categoryRepository.findByActiveTrueOrderByNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public Category getById(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found."));
    }

    @Override
    @Transactional(readOnly = true)
    public Category getActiveById(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found."));
        if (!category.isActive()) {
            throw new BusinessRuleException("This category is no longer available.");
        }
        return category;
    }

    @Override
    public Category create(CategoryForm form) {
        if (categoryRepository.existsByNameIgnoreCase(form.getName().trim())) {
            throw new BusinessRuleException("A category with this name already exists.");
        }
        return categoryRepository.save(new Category(form.getName(), form.getDescription()));
    }

    @Override
    public void update(UUID id, String name, String description) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found."));
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("Category name is required.");
        }
        String cleanName = name.trim();
        if (cleanName.length() > 80) {
            throw new BusinessRuleException("Category name is too long (maximum 80 characters).");
        }
        if (description != null && description.trim().length() > 255) {
            throw new BusinessRuleException("Description is too long (maximum 255 characters).");
        }
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(cleanName, id)) {
            throw new BusinessRuleException("Another category already uses this name.");
        }
        category.update(cleanName, description);
        categoryRepository.save(category);
    }

    @Override
    public void toggleActive(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found."));
        category.toggleActive();
        categoryRepository.save(category);
    }
}
