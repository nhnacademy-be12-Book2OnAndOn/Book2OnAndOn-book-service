package org.nhnacademy.book2onandonbookservice.repository;

import java.util.Optional;
import org.nhnacademy.book2onandonbookservice.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    Optional<Author> findByAuthorName(String authorName);
}
