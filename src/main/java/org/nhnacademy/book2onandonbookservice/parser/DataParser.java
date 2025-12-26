package org.nhnacademy.book2onandonbookservice.parser;

import java.io.IOException;
import java.util.List;
import org.nhnacademy.book2onandonbookservice.dto.DataParserDto;
import org.springframework.core.io.Resource;

public interface DataParser {
    String getFileType();

    List<DataParserDto> parsing(Resource resource) throws IOException;

}