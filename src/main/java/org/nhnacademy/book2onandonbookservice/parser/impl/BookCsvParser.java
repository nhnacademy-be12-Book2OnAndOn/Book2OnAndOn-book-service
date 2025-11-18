package org.nhnacademy.book2onandonbookservice.parser.impl;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.DataParserDto;
import org.nhnacademy.book2onandonbookservice.exception.DataParserException;
import org.nhnacademy.book2onandonbookservice.parser.DataParser;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BookCsvParser implements DataParser {
    private static final String ISBN_13 = "ISBN_THIRTEEN_NO";
    private static final String ISBN_10 = "ISBN_NO";
    private static final String TITLE = "TITLE_NM";
    private static final String AUTHOR = "AUTHR_NM";
    private static final String SEQ_NO = "SEQ_NO";
    private static final String PUBLISHER = "PUBLISHER_NM";
    private static final String PRICE = "PRC_VALUE";
    private static final String PUBLISHED_AT = "TWO_PBLICTE_DE";
    private static final String DESCRIPTION = "BOOK_INTRCN_CN";
    private static final String IMAGE_URL = "IMAGE_URL_L";

    @Override
    public String getFileType() {
        return "csv";
    }

    @Override
    public List<DataParserDto> parsing(File file) throws IOException {
        List<DataParserDto> dtoList = new ArrayList<>();
        int lineNum = 1;

        try (CSVReader csvReader = new CSVReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String[] headers = csvReader.readNext();
            if (headers == null || headers.length == 0) {
                throw new DataParserException("CSV 파일이 비어있거나 헤더가 없습니다: " + file.getName());
            }

            Map<String, Integer> headerMap = createHeaderMap(headers);
            validateRequiredHeaders(headerMap);

            String[] values;
            while ((values = csvReader.readNext()) != null) {
                lineNum++;
                try {
                    DataParserDto dto = createDtoFromValues(values, headerMap);
//                    validateDto(dto, values, headerMap);
                    dtoList.add(dto);
                } catch (DataParserException e) {
                    log.warn("라인 {} 스킵: 데이터 유효성 검사 실패. (이유: {})", lineNum, e.getMessage());
                }
            }
        } catch (CsvValidationException e) {
            throw new DataParserException("CSV 파일 형식이 올바르지 않습니다. (라인 " + lineNum + " 근처)", e);
        }
        return dtoList;
    }


    private DataParserDto createDtoFromValues(String[] values, Map<String, Integer> headers) {
        String isbn13 = getValue(values, headers.getOrDefault(ISBN_13, -1));
        String isbn10 = getValue(values, headers.getOrDefault(ISBN_10, -1));
        String title = getValue(values, headers.getOrDefault(TITLE, -1));

        String ISBN = duplicateIsbn(isbn13, isbn10);

        if (title.isEmpty()) {
            throw new DataParserException("필수 값 'TITLE_NM'이 비어있습니다.");
        }

        String seqNo = getValue(values, headers.getOrDefault(SEQ_NO, -1));
        String rawAuthorStr = getValue(values, headers.getOrDefault(AUTHOR, -1));
        String publisherName = getValue(values, headers.getOrDefault(PUBLISHER, -1));
        String priceStr = getValue(values, headers.getOrDefault(PRICE, -1));
        String publishedStr = getValue(values, headers.getOrDefault(PUBLISHED_AT, -1));
        String description = getValue(values, headers.getOrDefault(DESCRIPTION, -1));
        String imageUrl = getValue(values, headers.getOrDefault(IMAGE_URL, -1));

        return new DataParserDto(
                seqNo,
                ISBN,
                title,
                rawAuthorStr,
                publisherName,
                priceStr,
                publishedStr,
                description,
                imageUrl
        );
    }

//    private void validateDto(DataParserDto dto, String[] values, Map<String, Integer> headers) {
//        if (dto.getIsbn().length() > 20) {
//            throw new DataParserException("ISBN길이가 20자를 초과합니다. (값: " + dto.getIsbn() + ")");
//        }
//        if (dto.getTitle().length() > 255) {
//            throw new DataParserException("TITLE_NM 길이가 255자를 초과합니다. (값: " + dto.getTitle() + ")");
//        }

    /// /        if (dto.getPublishedAt() == null) { /            String rawDate = getValue(values,
    /// headers.getOrDefault(PUBLISHED_AT, -1)); /            throw new DataParserException("필 수 값 'TWO_PBLICTE_DE' 가
    /// 비어있거나 형식이 잘못됐습니다. (원본값 : " + rawDate + ")"); /        } /        if (dto.getListPrice() <= 0) { /
    /// String rawPrice = getValue(values, headers.getOrDefault(PRICE, -1)); /            throw new
    /// DataParserException("필수 값 'PRC_VALUE'가 0이하이거나 형식이 잘못되었습니다. (원본값 : " + rawPrice + ")"); /        }
//
//        if (dto.getPublisherName() == null || dto.getPublisherName().isEmpty()) {
//            throw new DataParserException("필수 값 'PUBLISHER_NM'이 비어있습니다.");
//        }
//
//        if (dto.getPublisherName().length() > 50) {
//            throw new DataParserException("PUBLISHER_NM 길이가 50자를 초과합니다. (값: " + dto.getPublisherName() + ")");
//        }
//
//        if (dto.getAuthors() == null || dto.getAuthors().isEmpty()) {
//            throw new DataParserException("필수 값 'AUTHR_NM (지은이)가 비어있습니다.");
//        }
//        for (String authorName : dto.getAuthors()) {
//            if (authorName.length() > 50) {
//                throw new DataParserException("AUTHR_NM (지은이) 이름이 50자를 초과합니다. (값: " + authorName + ")");
//            }
//        }
//
//        if (dto.getTranslators() != null) {
//            for (String translatorName : dto.getTranslators()) {
//                if (translatorName.length() > 50) {
//                    throw new DataParserException("AUTHR_NM (옮긴이) 이름이 50자를 초과합니다.(값: " + translatorName + ")");
//                }
//            }
//        }
//
//        if (dto.getImageUrl() != null && dto.getImageUrl().length() > 255) {
//            throw new DataParserException(IMAGE_URL + " 길이가 255자를 초과합니다. (값: " + dto.getImageUrl() + ")");
//        }
//    }
    private String getValue(String[] values, int index) {
        if (index < 0 || index >= values.length) {
            return "";
        }

        String value = values[index];

        if (value == null) {
            return "";
        }
        value = value.trim();
        return value.equalsIgnoreCase("nan") ? "" : value;
    }

    private String duplicateIsbn(String isbn13, String isbn10) {
        if (isbn13 != null && !isbn13.isEmpty()) {
            return isbn13;
        }
        if (isbn10 != null && !isbn10.isEmpty()) {
            return isbn10;
        }

        throw new DataParserException("필수 값 'ISBN'이 비어있습니다. (13자리, 10자리 모두 비어있음)");
    }

    private Map<String, Integer> createHeaderMap(String[] headers) {
        Map<String, Integer> headerMap = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            headerMap.put(headers[i].trim(), i);
        }
        return headerMap;
    }

    private void validateRequiredHeaders(Map<String, Integer> headerMap) {

        String[] criticalHeader = {
                TITLE, AUTHOR, PUBLISHER, PRICE, PUBLISHED_AT
        };

        if (!headerMap.containsKey(ISBN_13) && !headerMap.containsKey(ISBN_10)) {

            throw new DataParserException("CSV 파일 헤더에 필수 컬럼이 없습니다: " + ISBN_13 + "또는 " + ISBN_10);
        }

        for (String header : criticalHeader) {
            if (!headerMap.containsKey(header)) {
                throw new DataParserException("CSV파일 헤더에 필수 컬럼이 없습니다: " + header);
            }
        }
    }
}
