package org.nhnacademy.book2onandonbookservice.repository;

import java.util.List;
import java.util.Optional;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {
    @Query("SELECT b FROM Book b "
            + "WHERE (b.priceStandard = 0 OR b.description IS NULL OR b.description = '') "
            + "AND b.status != 'BOOK_DELETED'")
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

    /// 신간 도서 조회용 (정렬 O)
    @Query("SELECT DISTINCT b FROM Book b JOIN b.bookCategories bc WHERE b.status='ON_SALE' AND bc.category.id IN :categoryIds ORDER BY b.publishDate DESC")
    Page<Book> findBooksByCategoryIdsSorted(@Param("categoryIds") List<Long> categoryIds, Pageable pageable);

    /// 검색 동기화용 (정렬 X)
    @Query("""
            SELECT DISTINCT b
            FROM Book b
            JOIN b.bookCategories bc
            WHERE bc.category.id IN :categoryIds
            """)
    Page<Book> findBooksByCategoryIds(@Param("categoryIds") List<Long> categoryIds, Pageable pageable);

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

    //주문 후 재고 차감 로직
    @Modifying(clearAutomatically = true) // 쿼리 실행 후 영속성 컨텍스트 초기화 (데이터 동기화)
    @Query("UPDATE Book b SET b.stockCount = b.stockCount - :quantity " +
            "WHERE b.id = :id AND b.stockCount >= :quantity")
    int decreaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    // 판매중 + 좋아요순 + 페이징
    Page<Book> findByStatusOrderByLikeCountDesc(BookStatus status, Pageable pageable);

    //주문 취소 후 재고 증감 로직
    @Modifying(clearAutomatically = true) // 쿼리 실행 후 영속성 컨텍스트 초기화 (데이터 동기화)
    @Query("UPDATE Book b SET b.stockCount = b.stockCount + :quantity " +
            "WHERE b.id = :id")
    int increaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    //판매 중 이거나 재고 없음인 책만 조회 (삭제된 책 제외)
    Page<Book> findByStatusNot(BookStatus status, Pageable pageable);

    List<Book> findAllByIdGreaterThan(Long idIsGreaterThan, Pageable limit);

    interface BookIdAndIsbn {
        Long getId();

        String getIsbn();
    }
}
