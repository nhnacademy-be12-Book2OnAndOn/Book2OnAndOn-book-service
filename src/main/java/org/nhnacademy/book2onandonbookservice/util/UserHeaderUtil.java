package org.nhnacademy.book2onandonbookservice.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class UserHeaderUtil {

    private static final String HEADER_USER_ID = "X-USER-ID";
    private static final String HEADER_USER_ROLE = "X-USER-ROLE";

    public Long getUserId() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }

        String userIdStr = request.getHeader(HEADER_USER_ID);
        if (userIdStr == null || userIdStr.isEmpty()) {
            return null;
        }

        try {
            return Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getUserRole() {
        HttpServletRequest request = getRequest();
        return (request != null) ? request.getHeader(HEADER_USER_ROLE) : null;
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes(); // spring은 요청이 들어오면 저 컨텍스트 홀더에 요청 정보를 저장해 두기 때문에 홀더에서 꺼내씀
        return (attributes != null) ? attributes.getRequest() : null;
    }
}

/**
 * 쓰는 방법 참고
 *
 * @RestController
 * @RequiredArgsConstructor public class BookController {
 * <p>
 * private final UserHeaderUtil userHeaderUtil; // 주입
 * @PostMapping("/books/{bookId}/likes") public ResponseEntity<String> likeBook(@PathVariable Long bookId) { Long userId
 * = userHeaderUtil.getUserId(); // 헤더에서 ID 꺼내기
 * <p>
 * if (userId == null) { return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다."); }
 * <p>
 * // 좋아요 로직 수행... return ResponseEntity.ok("좋아요 처리됨"); } }
 */