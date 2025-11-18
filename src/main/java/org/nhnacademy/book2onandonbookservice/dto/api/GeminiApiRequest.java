package org.nhnacademy.book2onandonbookservice.dto.api;

import java.util.Collections;
import java.util.List;
import lombok.Getter;

@Getter
public class GeminiApiRequest {

    private final List<Content> contents;

    public GeminiApiRequest(String textPrompt) {
        Part part = new Part(textPrompt);
        Content content = new Content(Collections.singletonList(part));
        this.contents = Collections.singletonList(content);
    }

    @Getter
    private static class Content {
        private final List<Part> parts;

        public Content(List<Part> parts) {
            this.parts = parts;
        }
    }

    @Getter
    private static class Part {
        private final String text;

        public Part(String text) {
            this.text = text;
        }
    }
}
