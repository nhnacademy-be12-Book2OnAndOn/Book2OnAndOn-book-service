package org.nhnacademy.book2onandonbookservice.repository;

import java.util.Optional;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByTagName(String tagName);
}
