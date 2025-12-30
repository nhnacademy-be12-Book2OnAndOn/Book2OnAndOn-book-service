package org.nhnacademy.book2onandonbookservice.service.enrichment;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.AladinApiClient;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.domain.EnrichmentStatus;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
import org.nhnacademy.book2onandonbookservice.entity.*;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundBookException;
import org.nhnacademy.book2onandonbookservice.repository.*;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchIndexService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookEnrichmentService {

    private final BookRepository bookRepository;
    private final AladinApiClient aladinApiClient;
    private final CategoryEnrichmentService categoryService;
    private final TagEnrichmentService tagService;
    private final BookEnrichmentTaskRepository taskRepository;

    private final PublisherRepository publisherRepository;
    private final ContributorRepository contributorRepository;
    private final BookPublisherRepository bookPublisherRepository;
    private final BookContributorRepository bookContributorRepository;
    private final BookSearchIndexService bookSearchIndexService;

    private static final Pattern CONTRIBUTOR_PATTERN = Pattern.compile("^([^(]*)\\s*\\(([^)]*)\\)$");
    private static final double DEFAULT_DISCOUNT_RATE = 0.1;
    private final ImageUploadService imageUploadService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enrichBookData(BookEnrichmentTask task) {
        Long bookId = task.getBookId();

        Book book = bookRepository.findById(bookId)
                .orElseThrow(()-> new NotFoundBookException(bookId) );


        if(book.getStatus() == BookStatus.BOOK_DELETED){
            return;
        }

        boolean needReindex = false;

        if(shouldProcessAladin(task)){
            processAladinEnrichment(task, book);
            if(task.getAladinStatus() == EnrichmentStatus.DONE ||
            task.getAladinStatus()==EnrichmentStatus.NOT_FOUND){
                needReindex=true;
            }
        }

        if(shouldProcessAi(task)){
            processAiEnrichment(task, book);
            if(task.getAiStatus() == EnrichmentStatus.DONE ||
                    task.getAiStatus()==EnrichmentStatus.NOT_FOUND){
                needReindex=true;
            }
        }

        taskRepository.save(task);

        if(needReindex){
            try{
                bookSearchIndexService.index(book);
                log.info("[ES Sync] 보강 결과 재색인 완료 (BookId: {}, Aladin:{}, AI:{}", bookId, task.getAladinStatus(), task.getAiStatus());
            }catch (Exception e){
                log.error("[ES Sync] 재색인 실패 (BookId:{})", bookId, e);
                //로그만 찍고 넘어감
            }
        }
    }

    private void processAladinEnrichment(BookEnrichmentTask task, Book book){
        try{
            AladinApiResponse.Item aladinData = aladinApiClient.searchByIsbn(book.getIsbn());

            if(aladinData == null){
                task.markAladinNotFound();
                log.warn("[알라딘] 정보 없음 (BookId:{})", book.getId());
            }else{
                categoryService.enrich(book, aladinData);

                enrichBasicInfo(book,aladinData);
                enrichPublisher(book, aladinData.getPublisher());
                enrichContributors(book, aladinData.getAuthor());
                enrichThumbnail(book, aladinData.getCover());

                task.markAladinDone();

                log.info("[알라딘] 보강 성공 (BookId:{})", book.getId());
            }
        } catch (Exception e) {
            log.error("[알라딘] 호출 실패: {}", e.getMessage() );
            task.markAladinFailed(e.getMessage());
            //알라딘 실패ㅐ도 AI는 진행되게
        }
    }

    private void processAiEnrichment(BookEnrichmentTask task, Book book){
        try{
            String description = book.getDescription();
            tagService.enrich(book,book.getTitle(), description, book.getIsbn());

            task.markAiDone();
            log.info("[AI] 태그/챕터 생성 성공 (BookId:{})", book.getId());
        }catch (Exception e){
        log.error("[AI] 생성 실패: {}", e.getMessage());
        task.markAiFailed(e.getMessage());
        }
    }

    private boolean shouldProcessAladin(BookEnrichmentTask task){
        if(task.getAladinStatus() == EnrichmentStatus.DONE ||
        task.getAladinStatus() == EnrichmentStatus.NOT_FOUND){
            return false;
        }

        return task.getAladinStatus() == EnrichmentStatus.PENDING ||
                (task.getAladinStatus() == EnrichmentStatus.FAILED && task.getAladinRetryCount() < 3);
    }

    private boolean shouldProcessAi(BookEnrichmentTask task){
        if(task.getAiStatus() == EnrichmentStatus.DONE){
            return false;
        }
        return task.getAiStatus() == EnrichmentStatus.PENDING ||
                (task.getAiStatus() == EnrichmentStatus.FAILED && task.getAiRetryCount() < 3);
    }

    private void enrichBasicInfo(Book book, AladinApiResponse.Item item){
        if(!StringUtils.hasText(book.getTitle()) || (item.getTitle() != null && book.getTitle().length() < item.getTitle().length())){
            book.setTitle(item.getTitle());
        }

        if(!StringUtils.hasText(book.getDescription()) && StringUtils.hasText(item.getDescription())){
            book.setDescription(item.getDescription());
        }

        if (item.getPriceStandard() != null) {
            long standardPrice = item.getPriceStandard();
            book.setPriceStandard(standardPrice);

            // 10원 단위 절삭을 위한 로직 (보통 책 가격은 10원 단위)
            long salesPrice = (long) (standardPrice * (1 - DEFAULT_DISCOUNT_RATE));
            salesPrice = (salesPrice / 10) * 10; // 1원 단위 버림

            book.setPriceSales(salesPrice);
        }

        if(book.getPublishDate() == null && StringUtils.hasText(item.getPubDate())){
            try{
                book.setPublishDate(LocalDate.parse(item.getPubDate(), DateTimeFormatter.ISO_DATE));
            } catch (Exception e) {
                log.warn("날짜 파싱 실패(BookId: {}): {}", book.getId(), item.getPubDate());
            }
        }
    }

    private void enrichPublisher(Book book, String publisherName){
        if(!StringUtils.hasText(publisherName)) return;

        String cleanName = publisherName.trim();

        Publisher publisher = publisherRepository.findByPublisherName(cleanName)
                .orElseGet(() -> {
                    try {
                        return publisherRepository.save(Publisher.builder().publisherName(cleanName).build());
                    } catch (Exception e) {
                        return publisherRepository.findByPublisherName(cleanName).orElseThrow();
                    }
                });

        boolean exists = bookPublisherRepository.existsByBookAndPublisher(book, publisher);

        if(!exists){
            BookPublisher bookPublisher = BookPublisher.builder()
                    .book(book)
                    .publisher(publisher)
                    .build();

            bookPublisherRepository.save(bookPublisher);

            book.getBookPublishers().add(bookPublisher);
        }

    }

    private void enrichContributors(Book book, String authorString){
        if(!StringUtils.hasText(authorString)) return;

        String[] authors = authorString.split(",");

        for(String raw : authors){
            String token = raw.trim();
            if(token.isEmpty()) continue;

            String name;
            String role;

            Matcher matcher = CONTRIBUTOR_PATTERN.matcher(token);
            if(matcher.find()){
                name= matcher.group(1).trim();
                role = matcher.group(2).trim();
            }else{
                name = token;
                role="지은이";
            }

            String finalName = name;
            Contributor contributor = contributorRepository.findByContributorName(finalName)
                    .orElseGet(() -> {
                        try {
                            return contributorRepository.save(Contributor.builder().contributorName(finalName).build());
                        } catch (Exception e) {
                            return contributorRepository.findByContributorName(finalName).orElseThrow();
                        }
                    });

            boolean exists = bookContributorRepository.existsByBookAndContributorAndRoleType(book, contributor, role);

            if(!exists){
                BookContributor bookContributor = BookContributor.builder()
                        .book(book)
                        .contributor(contributor)
                        .roleType(role)
                        .build();

                bookContributorRepository.save(bookContributor);
            }
        }
    }


    private void enrichThumbnail(Book book, String coverUrl){
        if(!StringUtils.hasText(coverUrl)) return;

        String newInternalUrl = imageUploadService.uploadImageFromUrl(coverUrl);
        if (newInternalUrl == null) return;

        if (StringUtils.hasText(book.getThumbnail())) {
            // 아까 수정한 remove 메서드가 URL을 파싱해서 알아서 지워줍니다.
            imageUploadService.remove(book.getThumbnail());
        }
        book.setThumbnail(newInternalUrl);

        if (book.getImages() != null) {
            book.getImages().forEach(img -> {
                // BookImage 엔티티가 boolean 필드이므로 getter는 isThumbnail() 입니다.
                if (img.isThumbnail()) {
                    img.setThumbnail(false);
                }
            });
        }

        BookImage newBookImage = BookImage.builder()
                .book(book)
                .imagePath(newInternalUrl)
                .isThumbnail(true)
                .build();

        book.getImages().add(newBookImage);
        log.info("[이미지 보강] 썸네일 교체 및 기존 파일 삭제 완료 (BookId: {})", book.getId());
    }


}