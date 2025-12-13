package org.nhnacademy.book2onandonbookservice.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ElasticsearchIndexConfig {

    private final ElasticsearchClient elasticsearchClient;
    private static final String INDEX_NAME = "book2onandon-books";
    private static final String INDEX_DEFINITION_PATH = "static/elastic-index-definition.json";

    @PostConstruct
    public void initIndex() {
        try {
            boolean exists = elasticsearchClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(INDEX_NAME)))
                    .value();

            if (exists) {
                log.info("인덱스 [{}]가 이미 존재합니다.", INDEX_NAME);
                return;
            }

            log.info("인덱스 [{}] 생성 시작...", INDEX_NAME);

            // JSON 파일 읽기
            ClassPathResource resource = new ClassPathResource(INDEX_DEFINITION_PATH);
            try (InputStream inputStream = resource.getInputStream()) {
                String indexDefinition = new String(inputStream.readAllBytes());

                // 인덱스 생성
                elasticsearchClient.indices().create(c -> c
                        .index(INDEX_NAME)
                        .withJson(new java.io.StringReader(indexDefinition))
                );

                log.info("인덱스 [{}] 생성 완료", INDEX_NAME);
            }

        } catch (IOException e) {
            log.error("인덱스 생성 실패", e);
            throw new RuntimeException("Failed to initialize Elasticsearch index", e);
        }
    }
}