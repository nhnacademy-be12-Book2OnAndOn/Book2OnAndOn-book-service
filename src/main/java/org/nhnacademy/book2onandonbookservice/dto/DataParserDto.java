package org.nhnacademy.book2onandonbookservice.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public class DataParserDto {

    private final String seqNo;
    private final String isbn;
    private final String title;
    private final String description;
    private final LocalDate publishedAt;
    private final long listPrice;
    private final long salePrice;
    private final String publisherName;

    private final List<String> authors;
    private final List<String> translators;

    private final String imageUrl;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public DataParserDto(String seqNo, String isbn, String title, String rawAuthorStr, String publisherName,
                         String priceStr, String publishedAtStr, String description, String imageUrl
    ) {
        this.seqNo = seqNo;
        this.isbn = isbn;
        this.title = title;
        this.description = description;
        this.publishedAt = parseDate(publishedAtStr);
        this.listPrice = parsePrice(priceStr);
        this.salePrice = this.listPrice;
        this.publisherName = publisherName;
        this.imageUrl = (imageUrl == null || imageUrl.isEmpty()) ? null : imageUrl;

        if (rawAuthorStr == null || rawAuthorStr.isEmpty()) {
            this.authors = Collections.emptyList();
            this.translators = Collections.emptyList();
        } else {
            List<String> participants = Arrays.asList(rawAuthorStr.split("\\s*, \\s*"));

            this.authors = participants.stream()
                    .filter(s -> s.contains("(지은이)") || !s.contains(")"))
                    .map(s -> s.replaceAll("\\s*\\(.*?\\)\\s*", "").trim())
                    .collect(Collectors.toList());

            this.translators = participants.stream()
                    .filter(s -> s.contains("(옮긴이)") || !s.contains(")"))
                    .map(s -> s.replaceAll("\\s*\\(.*?\\)\\s*", "").trim())
                    .collect(Collectors.toList());
        }

    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty() || dateStr.equalsIgnoreCase("nan")) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private long parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isEmpty() || priceStr.equalsIgnoreCase("nan")) {
            return 0L;
        }
        try {
            return (long) Double.parseDouble(priceStr);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
