package org.nhnacademy.book2onandonbookservice.repository;

import java.util.List;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookRepository extends JpaRepository<Book, Long> {

    boolean existsByIsbn(String isbn);


    @Query("SELECT b FROM Book b " +
            "LEFT JOIN FETCH b.bookCategories bc " +
            "WHERE b.priceStandard = 0 " +
            "OR b.description IS NULL OR b.description = '' " +
            "OR bc.id IS NULL")
    List<Book> findBooksNeedingEnrichment(Pageable pageable);


    List<BookIdAndIsbn> findByIsbnIn(List<String> isbns);

    interface BookIdAndIsbn {
        Long getId();

        String getIsbn();
    }

    @Query("SELECT b FROM Book b WHERE (b.description IS NOT NULL AND b.description != '') AND SIZE(b.bookTags) = 0")
    List<Book> findBooksNeedingTags(Pageable pageable);
}
