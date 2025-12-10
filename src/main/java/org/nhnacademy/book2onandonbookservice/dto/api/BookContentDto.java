package org.nhnacademy.book2onandonbookservice.dto.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BookContentDto {

    private List<String> tags;
    private String chapter;

    /**
     * 빈 객체 생성
     */
    public static BookContentDto empty(){
        return new BookContentDto(Collections.emptyList(), "");
    }

    /**
     * 태그가 비었는지 확인
     */
    public boolean hasNoTags(){
        return tags==null || tags.isEmpty();
    }

    /**
     * 목차가 비었는지 확인
     */
    public boolean hasNoChapter(){
        return chapter==null || chapter.isEmpty();
    }

}
