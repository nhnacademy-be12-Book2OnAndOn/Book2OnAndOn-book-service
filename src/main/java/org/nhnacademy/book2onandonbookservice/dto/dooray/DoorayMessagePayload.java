package org.nhnacademy.book2onandonbookservice.dto.dooray;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DoorayMessagePayload {
    private String botName;
    private String text;
    private List<Attachment> attachments;


    @Getter
    @Builder
    public static class Attachment {
        private String title;
        private String titleLink;
        private String text;
        private String botIconImage;
        private String color;
    }
}
