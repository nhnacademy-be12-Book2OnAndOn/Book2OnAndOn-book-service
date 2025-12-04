package org.nhnacademy.book2onandonbookservice.service.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.event.TagUpdatedEvent;
import org.nhnacademy.book2onandonbookservice.repository.TagRepository;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {
    @InjectMocks
    private TagService tagService;

    @Mock
    private TagRepository tagRepository;


    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Test
    @DisplayName("태그 이름 변경 성공")
    void updateTagName() {
        Long tagId = 1L;
        String oldName = "ddd";
        String newName = "ttt";
        Tag tag = Tag.builder().id(tagId).tagName(oldName).build();

        given(tagRepository.findById(tagId)).willReturn(Optional.of(tag));

        Tag result = tagService.updateTagName(tagId, newName);

        assertThat(result.getTagName()).isEqualTo(newName);

        verify(applicationEventPublisher).publishEvent(any(TagUpdatedEvent.class));
    }

    @Test
    @DisplayName("태그 이름 변경 무시")
    void updateTagName_ignore() {
        Long tagId = 1L;
        String oldName = "ddd";

        Tag tag = Tag.builder().id(tagId).tagName(oldName).build();

        given(tagRepository.findById(tagId)).willReturn(Optional.of(tag));
        Tag result = tagService.updateTagName(tagId, oldName);

        assertThat(result.getTagName()).isEqualTo(oldName);
        verify(applicationEventPublisher, never()).publishEvent(any());

    }

    @Test
    @DisplayName("태그 수정 실패")
    void updateTagName_Fail() {
        Long tagId = 999L;
        given(tagRepository.findById(tagId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.updateTagName(tagId, "New"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("태그를 찾을 수 없습니다.");

        verifyNoMoreInteractions(applicationEventPublisher);
    }


}