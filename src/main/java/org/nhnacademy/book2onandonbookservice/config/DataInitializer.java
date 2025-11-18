package org.nhnacademy.book2onandonbookservice.config;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.DataParserDto;
import org.nhnacademy.book2onandonbookservice.entity.Author;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookAuthor;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.BookPublisher;
import org.nhnacademy.book2onandonbookservice.entity.BookTranslator;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.nhnacademy.book2onandonbookservice.entity.Translator;
import org.nhnacademy.book2onandonbookservice.exception.DataParserException;
import org.nhnacademy.book2onandonbookservice.parser.DataParser;
import org.nhnacademy.book2onandonbookservice.parser.DataParserResolver;
import org.nhnacademy.book2onandonbookservice.repository.AuthorRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.PublisherRepository;
import org.nhnacademy.book2onandonbookservice.repository.TranslatorRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final DataParserResolver parserResolver;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    private final BookRepository bookRepository;
    private final PublisherRepository publisherRepository;
    private final AuthorRepository authorRepository;
    private final TranslatorRepository translatorRepository;

    private final Map<String, Publisher> publisherCache = new HashMap<>();
    private final Map<String, Author> AuthorCache = new HashMap<>();
    private final Map<String, Translator> TranslatorCache = new HashMap<>();

    @Override
    public void run(ApplicationArguments args) throws Exception {

        if (bookRepository.count() > 0) {
            log.info("데이터가 이미 초기화되어 있으므로, CSV 파일 로딩을 건너뜁니다.");
            return;
        }

        log.info("데이터 초기화 시작");

        Resource[] resources = resolver.getResources("classpath:/data/*.*");

        if (resources.length == 0) {
            log.warn("classpath:/data 폴더에서 파일을 찾을 수 없습니다.");
            return;
        }

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) {
                continue;
            }

            try {
                log.info("파일 처리 중: {}", filename);
                DataParser parser = parserResolver.getDataParser(filename);
                List<DataParserDto> dtoList = (List<DataParserDto>) parser.parsing(resource.getFile());

                int processedCount = 0;

                for (DataParserDto dto : dtoList) {
                    processSingleBook(dto);
                    processedCount++;
                }

                log.info("총 {}개의 레코드를 처리했습니다", processedCount);
            } catch (DataParserException | IOException | ClassCastException e) {
                log.error("파일 처리 실패: {}, (원인: {})", filename, e.getMessage(), e.getCause());
            }
        }

        log.info("데이터 초기화 완료");
    }

    @Transactional
    public void processSingleBook(DataParserDto dto) {
        if (bookRepository.existsByIsbn(dto.getIsbn())) {
            log.warn("이미 존재하는 ISBN입니다. (ISBN: {}), 저장 건너뜀", dto.getIsbn());
            return;
        }

        Publisher publisher = getOrCreatePublisher(dto.getPublisherName());

        List<Author> authors = dto.getAuthors().stream()
                .map(this::getOrCreateAuthor)
                .collect(Collectors.toList());

        List<Translator> translators = dto.getTranslators().stream()
                .map(this::getOrCreateTranslator)
                .collect(Collectors.toList());

        Book book = Book.builder()
                .isbn(dto.getIsbn())
                .title(dto.getTitle())
                .description(dto.getDescription())
                .publishDate(dto.getPublishedAt())
                .priceStandard((int) dto.getListPrice())
                .priceSales((int) dto.getSalePrice())
                .stockCount(999)
                .stockStatus("Available")
                .packed(true)
                .status(BookStatus.ON_SALE)
                .build();

        book.getBookPublishers().add(
                BookPublisher.builder()
                        .book(book)
                        .publisher(publisher)
                        .build()
        );

        for (Author author : authors) {
            book.getBookAuthors().add(
                    BookAuthor.builder()
                            .book(book)
                            .author(author)
                            .build()
            );
        }

        for (Translator translator : translators) {
            book.getBookTranslators().add(
                    BookTranslator.builder()
                            .book(book)
                            .translator(translator)
                            .build()
            );
        }

        if (dto.getImageUrl() != null) {
            book.getImages().add(
                    BookImage.builder()
                            .book(book)
                            .imagePath(dto.getImageUrl())
                            .build()
            );
        }

        bookRepository.save(book);
    }


    private Publisher getOrCreatePublisher(String name) {
        return publisherCache.computeIfAbsent(name, n -> publisherRepository.findByPublisherName(n)
                .orElseGet(() -> publisherRepository.save(Publisher.builder().publisherName(n).build())));
    }

    private Translator getOrCreateTranslator(String name) {
        return TranslatorCache.computeIfAbsent(name, n -> translatorRepository.findByTranslatorName(n)
                .orElseGet(() -> translatorRepository.save(Translator.builder().translatorName(n).build())));
    }

    private Author getOrCreateAuthor(String name) {
        return AuthorCache.computeIfAbsent(name, n -> authorRepository.findByAuthorName(n)
                .orElseGet(() -> authorRepository.save(Author.builder().authorName(n).build())));
    }
}
