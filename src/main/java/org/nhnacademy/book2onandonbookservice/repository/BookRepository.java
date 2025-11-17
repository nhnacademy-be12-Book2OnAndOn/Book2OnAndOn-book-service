package org.nhnacademy.book2onandonbookservice.repository;

import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, Long> {
}
