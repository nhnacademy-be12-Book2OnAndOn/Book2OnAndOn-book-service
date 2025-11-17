package org.nhnacademy.book2onandonbookservice.parser;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.exception.DataParserException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataParserResolver {

    private final List<DataParser> dataParserList;

    public DataParser getDataParser(String fileName) {
        if (fileName == null || fileName.trim().isEmpty() || fileName.lastIndexOf('.') <= 0) {
            String errorMessage = "유효하지 않은 파일이름이거나 확장자가 존재하지않습니다. (fileName: " + fileName + ")";
            log.error(errorMessage);
            throw new DataParserException(errorMessage);
        }
        return dataParserList.stream()
                .filter(parser -> parser.matchFileType(fileName))
                .findFirst()
                .orElseThrow(() -> {
                    String errorMessage = "지원하는 파서를 찾을 수 없습니다. (fileName: " + fileName + ")";
                    log.error(errorMessage);
                    return new DataParserException(errorMessage);
                });
    }
}
