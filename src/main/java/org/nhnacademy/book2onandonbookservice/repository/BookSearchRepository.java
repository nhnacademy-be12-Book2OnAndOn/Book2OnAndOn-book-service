package org.nhnacademy.book2onandonbookservice.repository;

import org.nhnacademy.book2onandonbookservice.service.search.BookSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface BookSearchRepository extends ElasticsearchRepository<BookSearchDocument, Long> {
}
