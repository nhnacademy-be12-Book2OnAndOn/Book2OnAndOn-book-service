package org.nhnacademy.book2onandonbookservice.repository;

import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByBookIdAndUserId(Long bookId, Long userId);

    Page<Review> findAllByBook(Book book, Pageable pageable);

    @Query("SELECT AVG(r.score) FROM Review r WHERE r.book= :book")
    Double getAverageScoreByBook(@Param("book") Book book);

    Page<Review> findAllByUserId(Long userId, Pageable pageable);
}
