package org.nhnacademy.book2onandonbookservice.service.tag;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.event.TagUpdatedEvent;
import org.nhnacademy.book2onandonbookservice.repository.TagRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 태그 업데이트 시 사용 이벤트
@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {
    private final TagRepository tagRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Tag updateTagName(Long tagId, String newName) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new IllegalArgumentException("태그를 찾을 수 없습니다. id=" + tagId));

        String oldName = tag.getTagName();

        // 이름이 같으면 이벤트 안 보냄
        if (Objects.equals(oldName, newName)) {
            log.info("태그 이름이 바뀌지 않았습니다. id={}, name={}" + tagId, newName);
            return tag;
        }

        // 영속 엔티티 변경 -> DB 반영
        tag.setTagName(newName);

        log.info("태그 이름 변경 사항이 업데이트 되었습니다. id={}, oldName={}, newName={}", tagId, oldName, newName);

        // 변경 감지 이벤트 발생
        eventPublisher.publishEvent(new TagUpdatedEvent(tagId, oldName, newName));

        return tag;
    }
}
