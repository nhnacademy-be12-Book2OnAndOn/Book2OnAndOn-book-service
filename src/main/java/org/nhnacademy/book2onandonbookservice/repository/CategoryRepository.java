package org.nhnacademy.book2onandonbookservice.repository;

import java.util.Optional;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByCategoryNameAndParent(String name, Category parent);
}
