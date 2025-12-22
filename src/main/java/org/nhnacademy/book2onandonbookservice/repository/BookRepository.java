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
import org.springframework.transaction.annotation.Transactional;

public interface BookRepository extends JpaRepository<Book, Long> {

    List<BookIdAndIsbn> findByIsbnIn(List<String> isbns);

    // Book 수정 시 연관관계를 한 번에 가져오기 위한 전용 쿼리
     @Query("""
                SELECT DISTINCT b FROM Book b
                LEFT JOIN FETCH b.category c
                LEFT JOIN FETCH b.bookContributors bct
                WHERE b.id = :bookId
                """)
    Optional<Book> findByIdWithRelations(Long bookId);

    /// 신간 도서 조회용 (정렬 O)
    @Query("""
        SELECT DISTINCT b FROM Book b
        LEFT JOIN FETCH b.category
        WHERE b.status = 'ON_SALE' 
        AND b.category.id IN :categoryIds 
        ORDER BY b.publishDate DESC
        """)
   Page<Book> findBooksByCategoryIdsSorted(@Param("categoryIds") List<Long> categoryIds, Pageable pageable);

    /// 검색 동기화용 (정렬 X)
    @Query("""
            SELECT b
            FROM Book b
            WHERE b.category.id IN :categoryIds
            """)
    Page<Book> findBooksByCategoryIds(@Param("categoryIds") List<Long> categoryIds, Pageable pageable);

    @Query("""
            SELECT DISTINCT b
            FROM Book b
            JOIN b.bookTags bt
            WHERE bt.tag.id = :tagId
            """)
    Page<Book> findByTagId(Long tagId, Pageable pageable);


    @Query("""
        SELECT b FROM Book b
        LEFT JOIN FETCH b.category
        WHERE b.status = 'ON_SALE'
        ORDER BY b.publishDate DESC
        """)
    Page<Book> findAllByOrderByPublishDateDesc(Pageable pageable);


    @Query("""
        SELECT DISTINCT b FROM Book b
        LEFT JOIN FETCH b.bookContributors bc
        LEFT JOIN FETCH bc.contributor
        LEFT JOIN FETCH b.bookPublishers bp
        LEFT JOIN FETCH bp.publisher
        LEFT JOIN FETCH b.bookTags bt
        LEFT JOIN FETCH bt.tag
        WHERE b.id IN :bookIds
        """)
    List<Book> findBooksWithDetails(@Param("bookIds") List<Long> bookIds);

    Page<Book> findByCategory_IdIn(List<Long> categoryIds, Pageable pageable);

    @Transactional
    @Modifying(clearAutomatically = true) // 쿼리 실행 후 영속성 컨텍스트 초기화 (데이터 불일치 방지)
    @Query("UPDATE Book b SET b.status = 'BOOK_DELETED' WHERE b.id = :bookId")
    void updateStatusToDeleted(@Param("bookId") Long bookId);

    //주문 후 재고 차감 로직
    @Modifying(clearAutomatically = true) // 쿼리 실행 후 영속성 컨텍스트 초기화 (데이터 동기화)
    @Query("UPDATE Book b SET b.stockCount = b.stockCount - :quantity " +
            "WHERE b.id = :id AND b.stockCount >= :quantity")
    int decreaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    // 판매중 + 좋아요순 + 페이징
    Page<Book> findByStatusOrderByLikeCountDesc(BookStatus status, Pageable pageable);


    //판매 중 이거나 재고 없음인 책만 조회 (삭제된 책 제외)
    Page<Book> findByStatusNot(BookStatus status, Pageable pageable);

    List<Book> findAllByIdGreaterThan(Long idIsGreaterThan, Pageable limit);

    // 재고만 쏙 가져오는 쿼리
    @Query("SELECT b.stockCount From Book b where b.id = :id")
    Integer findStockCountById(@Param("id") Long id);

    interface BookIdAndIsbn {
        Long getId();

        String getIsbn();
    }
}
