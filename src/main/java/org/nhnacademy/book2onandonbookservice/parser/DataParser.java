package org.nhnacademy.book2onandonbookservice.parser;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface DataParser {
    String getFileType();
    List parsing (File file) throws IOException;
    default boolean matchFileType(String fileName){
        return fileName.trim().toLowerCase().endsWith(getFileType().toLowerCase());
    }
}
