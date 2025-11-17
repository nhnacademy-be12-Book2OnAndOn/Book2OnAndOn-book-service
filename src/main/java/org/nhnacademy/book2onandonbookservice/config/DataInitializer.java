package org.nhnacademy.book2onandonbookservice.config;

import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.DataParserDto;
import org.nhnacademy.book2onandonbookservice.exception.DataParserException;
import org.nhnacademy.book2onandonbookservice.parser.DataParser;
import org.nhnacademy.book2onandonbookservice.parser.DataParserResolver;
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
    private final BookAuthorRepository bookAuthorRepository;
    private final TranslatorRepository translatorRepository;
    private final BookTranslatorRepository bookTranslatorRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
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
    }

    @Transactional
    public void processSingleBook(DataParserDto dto) {
        if (bookRepository.existsByBookIsbn(dto.getIsbn())) {
            return;
        }

        Publisher publisher = publisherRepository.findByPublisherName(dto.getPublisherName())
                .orElseGet(() -> {
                    Publisher newPublisher = Publisher.builder().publisherName(dto.getPublisherName()).build();
                    return publisherRepository.save(newPublisher);
                });

        Book book = Book.builder()
                .bookIsbn(dto.getIsbn())
                .publisher(publisher)
                .bookTitle(dto.getTitle())
                .bookDescription(dto.getDescription())
                .bookPublisherAt(dto.getPublishedAt())
                .bookListPrice(dto.getListPrice())
                .bookSalePrice(dto.getSalePrice())
                .bookViewCount(0L)
                .bookStock(999)
                .bookIsPacked(true)
                .build();

        Book savedBook = bookRepository.save(book);

        for (String authorName : dto.getAuthors()) {
            Author author = authorRepository.findByAuthorName(authorName)
                    .orElseGet(() -> {
                        Author newAuthor = Author.builder().authorName(authorName);
                        return authorRepository.save(newAuthor);
                    });

            BookAuthor bookAuthor = BookAuthor.builder().book(savedBook).author(author).build();
            bookRepository.save(bookAuthor);
        }

        for (String translatorName : dto.getTranslators()) {
            Translator translator = translatorRepository.findByTranslatorName(translatorName)
                    .orElseGet(() -> {
                        Translator newTranslator = Translator.builder().translatorName(translatorName).build();
                        return translatorRepository.save(newTranslator);
                    });

            BookTranslator bookTranslator = BookTranslator.builder().book(savedBook).translator(translator).build();
            bookTranslatorRepository.save(bookTranslator);
        }
    }
}
