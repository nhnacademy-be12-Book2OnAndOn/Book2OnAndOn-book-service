package org.nhnacademy.book2onandonbookservice.repository;

import java.util.Optional;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublisherRepository extends JpaRepository<Publisher, Long> {
    Optional<Publisher> findByPublisherName(String publisherName);
}
