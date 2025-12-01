package org.nhnacademy.book2onandonbookservice.repository;

import java.util.List;
import java.util.Optional;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {

    boolean existsByIsbn(String isbn);


    @Query("SELECT b FROM Book b " +
            "WHERE b.priceStandard = 0 " +
            "OR b.description IS NULL OR b.description = '' " +
            "OR b.bookCategories IS EMPTY")
    List<Book> findBooksNeedingEnrichment(Pageable pageable);


    List<BookIdAndIsbn> findByIsbnIn(List<String> isbns);

    @Query("SELECT b FROM Book b WHERE (b.description IS NOT NULL AND b.description != '') AND SIZE(b.bookTags) = 0")
    List<Book> findBooksNeedingTags(Pageable pageable);


    // Book 수정 시 연관관계를 한 번에 가져오기 위한 전용 쿼리
    @Query("""
            SELECT DISTINCT b FROM Book b
            LEFT JOIN FETCH b.bookCategories bc
            LEFT JOIN FETCH b.bookContributors bct
            WHERE b.id = :bookId
            """)
    Optional<Book> findByIdWithRelations(Long bookId);

    // 카테고리, 태그로 책 조회
    @Query("""
            SELECT DISTINCT b
            FROM Book b
            JOIN b.bookCategories bc
            WHERE bc.category.id = :categoryId
            """)
    Page<Book> findByCategoryId(Long categoryId, Pageable pageable);

    @Query("""
            SELECT DISTINCT b
            FROM Book b
            JOIN b.bookTags bt
            WHERE bt.tag.id = :tagId
            """)
    Page<Book> findByTagId(Long tagId, Pageable pageable);
    @Query("SELECT b FROM Book b JOIN b.bookCategories bc WHERE bc.category.id = :categoryId ORDER BY b.publishDate DESC")
    Page<Book> findNewArrivalsByCategoryId(@Param("categoryId") Long categoryId, Pageable pageable);

    Page<Book> findAllByOrderByPublishDateDesc(Pageable pageable);

    interface BookIdAndIsbn {
        Long getId();

        String getIsbn();
    }
}
