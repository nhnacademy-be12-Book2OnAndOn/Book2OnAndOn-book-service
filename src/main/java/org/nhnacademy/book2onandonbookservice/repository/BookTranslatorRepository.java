package org.nhnacademy.book2onandonbookservice.repository;

import org.nhnacademy.book2onandonbookservice.entity.BookTranslator;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookTranslatorRepository extends JpaRepository<BookTranslator, Long> {
}
