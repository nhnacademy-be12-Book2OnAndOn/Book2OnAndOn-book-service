package org.nhnacademy.book2onandonbookservice.repository;

import java.util.List;
import org.nhnacademy.book2onandonbookservice.entity.BookLike;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookLikeRepository extends JpaRepository<BookLike, Long> {
    long countByBookId(Long bookId);

    boolean existsByBookIdAndUserId(Long bookId, Long userId);

    void deleteByBookIdAndUserId(Long bookId, Long userId);


    @Query("select bl.book.id from BookLike bl where bl.userId = :userId")
    List<Long> findBookIdsByUserId(Long userId);

    @EntityGraph(attributePaths = {"book", "book.bookContributors", "book.bookPublisher"})
    Page<BookLike> findAllByUserId(Long userId, Pageable pageable);
}
