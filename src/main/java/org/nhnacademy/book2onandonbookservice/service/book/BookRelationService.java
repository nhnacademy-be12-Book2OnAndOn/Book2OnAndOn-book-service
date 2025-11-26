package org.nhnacademy.book2onandonbookservice.service.book;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookCategory;
import org.nhnacademy.book2onandonbookservice.entity.BookContributor;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.BookTag;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.entity.Contributor;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.nhnacademy.book2onandonbookservice.repository.ContributorRepository;
import org.nhnacademy.book2onandonbookservice.repository.PublisherRepository;
import org.nhnacademy.book2onandonbookservice.repository.TagRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookRelationService {

    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final PublisherRepository publisherRepository;
    private final ContributorRepository contributorRepository;

    /// 도서 등록 시 연관관계
    public void applyRelationsForCreate(Book book, BookSaveRequest request) {
        setCategories(book, request.getCategoryIds());
        setTags(book, request.getTagNames());
        setPublishers(book, request.getPublisherIds(), request.getPublisherName());
        setContributors(book, request.getContributorName());
        setImages(book, request.getImagePath());
    }

    /// 기여자(저자) 설정: "XXX, XXX" 등의 형태를 , 기준으로 분리. 빈 문자열이면 아무것도 추가하지 않음.
    private void setContributors(Book book, String contributorName) {
        if (StringUtils.isBlank(contributorName)) {
            return; // null or 빈 문자열이면 기여자 없음
        }

        List<String> names = Arrays.stream(contributorName.split(","))
                .map(String::trim)
                .filter(this::notBlank)
                .collect(Collectors.toList());

        for (String name : names) {
            Contributor contributor = contributorRepository.findByContributorName(name)
                    .orElseGet(() -> contributorRepository.save(
                            Contributor.builder()
                                    .contributorName(name)
                                    .build()
                    ));

            BookContributor bookContributor = BookContributor.builder()
                    .book(book)
                    .contributor(contributor)
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
    private void setTags(Book book, Set<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return;
        }

        for (String tagName : tagNames.stream().distinct().toList()) {
            if (StringUtils.isNotBlank(tagName)) {
                Tag tag = tagRepository.findByTagName(tagName)
                        .orElseGet(() -> tagRepository.save(Tag.builder()
                                .tagName(tagName)
                                .build()));

                BookTag bookTag = BookTag.builder()
                        .book(book)
                        .tag(tag)
                        .build();

                book.getBookTags().add(bookTag);
            }
        }
    }

    /// 카테고리 설정: 카테고리 존재 여부만 확인
    private void setCategories(Book book, List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }

        List<Category> categories = categoryRepository.findAllById(categoryIds);
        if (categories.size() != categoryIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 카테고리가 포함되어 있습니다.");
        }

        for (Category category : categories) {
            BookCategory bookCategory = BookCategory.builder()
                    .book(book)
                    .category(category)
                    .build();
            book.getBookCategories().add(bookCategory);
        }
    }

    /// 이미지 설정: 이미지 1장 기준
    private void setImages(Book book, String imagePath) {
        if (!notBlank(imagePath)) {
            return;
        }

        BookImage image = BookImage.builder()
                .book(book)
                .imagePath(imagePath)
                .build();
        book.getImages().add(image);
    }

    /// 도서 수정 시 연관관계
    public void applyRelationsForUpdate(Book book, BookSaveRequest request) {
        // 카테고리: null 이 아니면 전체 교체
        if (request.getCategoryIds() != null) {
            book.getBookCategories().clear();
            setCategories(book, request.getCategoryIds());
        }

        // 태그: null 이 아니면 전체 교체
        if (request.getTagNames() != null) {
            book.getBookTags().clear();
            setTags(book, request.getTagNames());
        }

        // 출판사: ID 목록 또는 이름이 들어온 경우 전체 교체
        if (request.getPublisherIds() != null || StringUtils.isNotBlank(request.getPublisherName())) {
            book.getBookPublishers().clear();
            setPublishers(book, request.getPublisherIds(), request.getPublisherName());
        }

        // 기여자: null 이면 건드리지 않고, 빈 문자열이면 모두 제거
        if (request.getContributorName() != null) {
            book.getBookContributors().clear();
            setContributors(book, request.getContributorName());
        }

        // 이미지: null 이면 건드리지 않고, 빈 문자열이면 모두 제거
        if (request.getImagePath() != null) {
            book.getImages().clear();
            setImages(book, request.getImagePath());
        }
    }

    /// 공통 문자열 유틸
    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
