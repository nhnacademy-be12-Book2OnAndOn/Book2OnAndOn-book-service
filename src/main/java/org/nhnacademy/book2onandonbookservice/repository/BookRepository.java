package org.nhnacademy.book2onandonbookservice.repository;

import java.util.List;
import java.util.Optional;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookRepository extends JpaRepository<Book, Long> {

    boolean existsByIsbn(String isbn);


    @Query("SELECT b FROM Book b " + "LEFT JOIN FETCH b.bookCategories bc " + "WHERE b.priceStandard = 0 "
            + "OR b.description IS NULL OR b.description = '' " + "OR bc.id IS NULL")
    List<Book> findBooksNeedingEnrichment(Pageable pageable);


    List<BookIdAndIsbn> findByIsbnIn(List<String> isbns);

    @Query("SELECT b FROM Book b WHERE (b.description IS NOT NULL AND b.description != '') AND SIZE(b.bookTags) = 0")
    List<Book> findBooksNeedingTags(Pageable pageable);


    // 연관관계 전체 Featch Join 조회 쿼리 추가 -> Book 수정 시 연관관계를 한 번에 가져오기 위한 전용 쿼리
    @Query("""
            SELECT DISTINCT b FROM Book b
            LEFT JOIN FETCH b.bookCategories bc
            LEFT JOIN FETCH b.bookContributors bct
            WHERE b.id = :bookId
            """)
    Optional<Book> findByIdWithRelations(Long bookId);


    interface BookIdAndIsbn {
        Long getId();

        String getIsbn();
    }
}
