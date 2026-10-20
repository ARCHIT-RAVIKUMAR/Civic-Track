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
    public void toggleActive(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found."));
        category.toggleActive();
        categoryRepository.save(category);
    }
}
