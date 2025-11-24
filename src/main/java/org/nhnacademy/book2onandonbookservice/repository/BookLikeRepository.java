package org.nhnacademy.book2onandonbookservice.repository;

import org.nhnacademy.book2onandonbookservice.entity.BookLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookLikeRepository extends JpaRepository<BookLike, Long> {

    long countByBookId(Long bookId);

    boolean existsByBookIdAndUserId(Long bookId, Long userId);
}
