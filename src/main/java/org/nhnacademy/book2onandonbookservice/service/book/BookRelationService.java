package org.nhnacademy.book2onandonbookservice.service.book;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.BookUpdateRequest;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookContributor;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.BookTag;
import org.nhnacademy.book2onandonbookservice.entity.BookTagPK;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.entity.Contributor;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.exception.ImageUploadException;
import org.nhnacademy.book2onandonbookservice.repository.BookTagRepository;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.nhnacademy.book2onandonbookservice.repository.ContributorRepository;
import org.nhnacademy.book2onandonbookservice.repository.PublisherRepository;
import org.nhnacademy.book2onandonbookservice.repository.TagRepository;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookRelationService {

    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final BookTagRepository bookTagRepository;
    private final PublisherRepository publisherRepository;
    private final ContributorRepository contributorRepository;
    private final ImageUploadService imageUploadService;

    /// 도서 등록 시 연관관계
    public void applyRelationsForCreate(Book book, BookSaveRequest request) {
        setCategory(book, request.getCategoryId());
        setTags(book, request.getTagNames());
        setPublishers(book, request.getPublisherIds(), request.getPublisherName());
        setContributors(book, request.getContributorName());

    }

    /// 기여자(저자) 설정: "XXX, XXX" 등의 형태를 , 기준으로 분리. 빈 문자열이면 아무것도 추가하지 않음.
    private void setContributors(Book book, String contributorName) {
        if (StringUtils.isBlank(contributorName)) {
            return; // null or 빈 문자열이면 기여자 없음
        }

        List<String> names = Arrays.stream(contributorName.split(","))
                .map(String::trim)
                .filter(this::notBlank)
                .toList();

        for (String name : names) {
            Contributor contributor = contributorRepository.findTopByContributorName(name)
                    .orElseGet(() -> contributorRepository.save(
                            Contributor.builder()
                                    .contributorName(name)
                                    .build()
                    ));

            BookContributor bookContributor = BookContributor.builder()
                    .book(book)
                    .contributor(contributor)
                    .roleType("AUTHOR") // 기본값
                    .build();
            book.getBookContributors().add(bookContributor);
        }

    }

    /// 출판사 설정: 기존 출판사 ID 목록 + 신규 출판사 이름 모두 허용. 둘 다 들어오는 경우 -> 둘 다 매핑
    private void setPublishers(Book book, List<Long> publisherIds, String publisherName) {

        if (publisherIds != null) {
            List<Publisher> publishers = publisherRepository.findAllById(publisherIds);

            for (Publisher publisher : publishers) {
                if (!book.hasPublisher(publisher)) {
                    book.addPublisher(publisher);
                }
            }
        }

        // 신규 출판사 이름 매핑
        if (StringUtils.isNotBlank(publisherName)) {
            Publisher publisher = publisherRepository.findByPublisherName(publisherName)
                    .orElseGet(() -> publisherRepository.save(
                            Publisher.builder()
                                    .publisherName(publisherName)
                                    .build()
                    ));

            if (!book.hasPublisher(publisher)) {
                book.addPublisher(publisher);
            }
        }
    }

    /// 태그 설정: 태그명이 없으면 무시, 없는 태그 -> 신규 생성
    private void setTags(Book book, Set<String> tagNamesInput) {
        Set<String> newTagNames = (tagNamesInput == null) ? new HashSet<>() :
                tagNamesInput.stream()
                        .filter(StringUtils::isNotBlank)
                        .map(String::trim)
                        .collect(Collectors.toSet());

        book.getBookTags().removeIf(bookTag ->
                !newTagNames.contains(bookTag.getTag().getTagName().trim())
        );

        Set<String> existingTagNames = book.getBookTags().stream()
                .map(bt -> bt.getTag().getTagName().trim())
                .collect(Collectors.toSet());

        for (String tagName : newTagNames) {
            if (!existingTagNames.contains(tagName)) {

                Tag tag = tagRepository.findByTagName(tagName)
                        .orElseGet(() -> tagRepository.save(Tag.builder()
                                .tagName(tagName)
                                .build()));

                BookTagPK pk = new BookTagPK(book.getId(), tag.getId());
                BookTag bookTag = BookTag.builder()
                        .pk(pk)
                        .book(book)
                        .tag(tag)
                        .build();

                book.getBookTags().add(bookTag);
            }
        }
    }

    /// 카테고리 설정: 단일 적용
    private void setCategory(Book book, Long categoryId) {
        if (categoryId == null) {
            book.setCategory(null);
            return;
        }

        Category category = categoryRepository.findById(categoryId)
                .orElse(null);

        if (category != null) {
            book.setCategory(category);
        } else {
            log.warn("존재하지 않는 카테고리 ID 무시됨: {}", categoryId);
        }
    }


    /// 도서 수정 시 연관관계
    public void applyRelationsForUpdate(Book book, BookUpdateRequest request) {
        // 카테고리: null 이 아니면 전체 교체
        if (request.getCategoryId() != null) {
            setCategory(book, request.getCategoryId());
        }

        // 태그: null 이 아니면 전체 교체
        if (request.getTagNames() != null) {
            setTags(book, request.getTagNames());
        }

        // 출판사: ID 목록 또는 이름이 들어온 경우 전체 교체
        if (request.getPublisherIds() != null || StringUtils.isNotBlank(request.getPublisherName())) {
            updatePublishersSafely(book, request.getPublisherIds(), request.getPublisherName());
        }

        // 기여자: null 이면 건드리지 않고, 빈 문자열이면 모두 제거
        if (request.getContributorName() != null) {
            updateContributorsSafely(book, request.getContributorName());
        }

    }

    private void updatePublishersSafely(Book book, List<Long> publisherIds, String publisherName) {
        Set<Publisher> targetPublishers = new HashSet<>();

        if (publisherIds != null && !publisherIds.isEmpty()) {
            targetPublishers.addAll(publisherRepository.findAllById(publisherIds));
        }

        if (StringUtils.isNotBlank(publisherName)) {
            Publisher namedPublisher = publisherRepository.findByPublisherName(publisherName)
                    .orElseGet(() -> publisherRepository.save(
                            Publisher.builder().publisherName(publisherName).build()
                    ));
            targetPublishers.add(namedPublisher);
        }

        Set<Long> targetIds = targetPublishers.stream().map(Publisher::getId).collect(Collectors.toSet());
        book.getBookPublishers().removeIf(bp -> !targetIds.contains(bp.getPublisher().getId()));

        for (Publisher publisher : targetPublishers) {
            if (!book.hasPublisher(publisher)) {
                book.addPublisher(publisher);
            }
        }
    }

    private void updateContributorsSafely(Book book, String contributorName) {
        if (contributorName.trim().isEmpty()) {
            book.getBookContributors().clear();
            return;
        }

        Set<String> targetNames = Arrays.stream(contributorName.split(","))
                .map(String::trim)
                .filter(this::notBlank)
                .collect(Collectors.toSet());

        Set<Contributor> targetContributors = new HashSet<>();
        for (String name : targetNames) {
            Contributor contributor = contributorRepository.findTopByContributorName(name)
                    .orElseGet(() -> contributorRepository.save(
                            Contributor.builder().contributorName(name).build()
                    ));
            targetContributors.add(contributor);
        }

        book.getBookContributors().removeIf(bc ->
                !targetNames.contains(bc.getContributor().getContributorName()));

        for (Contributor contributor : targetContributors) {
            boolean exists = book.getBookContributors().stream()
                    .anyMatch(bc -> bc.getContributor().getId().equals(contributor.getId()));

            if (!exists) {
                BookContributor newContributor = BookContributor.builder()
                        .book(book)
                        .contributor(contributor)
                        .roleType("지은이")
                        .build();
                book.getBookContributors().add(newContributor);
            }
        }
    }

    /// 공통 문자열 유틸
    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
