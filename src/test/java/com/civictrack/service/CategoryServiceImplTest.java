package com.civictrack.service;

import com.civictrack.entity.Category;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.repository.CategoryRepository;
import com.civictrack.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock private CategoryRepository categoryRepository;

    private CategoryServiceImpl service;
    private Category category;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CategoryServiceImpl(categoryRepository);
        category = new Category("Old name", "Old description");
    }

    @Test
    void adminCanRenameACategory() {
        when(categoryRepository.findById(id)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("New name", id)).thenReturn(false);

        service.update(id, "  New name ", "");

        assertEquals("New name", category.getName());
        assertNull(category.getDescription());
        verify(categoryRepository).save(category);
    }

    @Test
    void duplicateNameIsRejected() {
        when(categoryRepository.findById(id)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Garbage", id)).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.update(id, "Garbage", null));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void blankNameIsRejected() {
        when(categoryRepository.findById(id)).thenReturn(Optional.of(category));

        assertThrows(BusinessRuleException.class, () -> service.update(id, "   ", null));
    }
}
