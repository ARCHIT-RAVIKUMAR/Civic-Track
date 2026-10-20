package com.civictrack.service;

import com.civictrack.dto.CategoryForm;
import com.civictrack.entity.Category;

import java.util.List;
import java.util.UUID;

public interface CategoryService {
    List<Category> findActive();

    List<Category> findAll();

    Category getActiveById(UUID id);

    Category create(CategoryForm form);

    void toggleActive(UUID id);
}
