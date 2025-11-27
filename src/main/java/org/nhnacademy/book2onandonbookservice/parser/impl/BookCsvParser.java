package org.nhnacademy.book2onandonbookservice.parser.impl;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.DataParserDto;
import org.nhnacademy.book2onandonbookservice.exception.DataParserException;
import org.nhnacademy.book2onandonbookservice.parser.DataParser;
import org.springframework.core.io.Resource;
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
    private static final String IMAGE_URL = "IMAGE_URL";
    private static final String VOLUME = "VLM_NM";

    @Override
    public String getFileType() {
        return "csv";
    }


    @Override
    public List<DataParserDto> parsing(Resource resource) throws IOException {
        List<DataParserDto> dtoList = new ArrayList<>(160000);
        int lineNum = 1;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
             CSVReader csvReader = new CSVReader(br)) {
            String[] headers = csvReader.readNext();
            if (headers == null || headers.length == 0) {
                throw new DataParserException("CSV 파일이 비어있거나 헤더가 없습니다: ");
            }

            Map<String, Integer> headerMap = createHeaderMap(headers);
            validateRequiredHeaders(headerMap);

            String[] values;
            while ((values = csvReader.readNext()) != null) {
                lineNum++;
                try {
                    DataParserDto dto = createDtoFromValues(values, headerMap);
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
        String volume = getValue(values, headers.getOrDefault(VOLUME, -1));

        return new DataParserDto(
                seqNo,
                ISBN,
                title,
                rawAuthorStr,
                publisherName,
                priceStr,
                publishedStr,
                description,
                imageUrl,
                volume
        );
    }

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
