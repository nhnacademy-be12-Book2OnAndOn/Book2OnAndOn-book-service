package org.nhnacademy.book2onandonbookservice.repository;

import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookContributor;
import org.nhnacademy.book2onandonbookservice.entity.Contributor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookContributorRepository extends JpaRepository<BookContributor, Long> {
    boolean existsByBookAndContributorAndRoleType(Book book, Contributor  contributor, String roleType);
}
