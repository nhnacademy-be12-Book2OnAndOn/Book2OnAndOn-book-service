package org.nhnacademy.book2onandonbookservice.dto;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;


@Getter
@Slf4j
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
    private static final DateTimeFormatter DATE_FORMATTER_NO_DASH = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String[] PARSE_PATTERS = {
            "yyyy-MM-dd",
            "yyyyMMdd",
            "yyyyMM",
            "yyyy"
    };

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

        String originalDateStr = dateStr;
        String cleanedDateStr = dateStr.trim();

        try {
            if (cleanedDateStr.length() == 10) { // "yyyy-MM-00"
                if (cleanedDateStr.endsWith("-00")) {
                    cleanedDateStr = cleanedDateStr.substring(0, 8) + "01";
                }
                if (cleanedDateStr.substring(5, 7).equals("00")) { // "yyyy-00-dd"
                    cleanedDateStr = cleanedDateStr.substring(0, 5) + "01" + cleanedDateStr.substring(7);
                }
            } else if (cleanedDateStr.length() == 8) { // "yyyyMM00"
                if (cleanedDateStr.endsWith("00")) {
                    cleanedDateStr = cleanedDateStr.substring(0, 6) + "01";
                }
                if (cleanedDateStr.substring(4, 6).equals("00")) { // "yyyy00dd"
                    cleanedDateStr = cleanedDateStr.substring(0, 4) + "01" + cleanedDateStr.substring(6);
                }
            } else if (cleanedDateStr.length() == 6 && cleanedDateStr.endsWith("00")) { // "yyyy00"
                cleanedDateStr = cleanedDateStr.substring(0, 4) + "01";
            }

            Date date = DateUtils.parseDate(cleanedDateStr, PARSE_PATTERS);
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (Exception pe) {
            return extractYearFromText(cleanedDateStr);
        }


    }

    private LocalDate extractYearFromText(String text) {
        // 1000~2999 사이의 4자리 숫자를 찾는 정규식
        Pattern pattern = Pattern.compile("(19|20)\\d{2}");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            try {
                int year = Integer.parseInt(matcher.group());
                return LocalDate.of(year, 1, 1); // 1월 1일로 저장
            } catch (NumberFormatException ignored) {
            }
        }

        log.warn("날짜 파싱 최종 실패 ({}): 지원하지 않는 형식이며 연도 추출도 실패했습니다.", text);
        return null;
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
