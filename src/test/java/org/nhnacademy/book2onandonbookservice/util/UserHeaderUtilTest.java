package org.nhnacademy.book2onandonbookservice.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class UserHeaderUtilTest {

    private UserHeaderUtil userHeaderUtil;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        userHeaderUtil = new UserHeaderUtil();
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void getUserId_ValidId() {
        request.addHeader("X-User-Id", "123");

        Long userId = userHeaderUtil.getUserId();

        assertThat(userId).isEqualTo(123L);
    }

    @Test
    void getUserId_InvalidFormat() {
        request.addHeader("X-User-Id", "invalid-id");

        Long userId = userHeaderUtil.getUserId();

        assertThat(userId).isNull();
    }

    @Test
    void getUserId_NullHeader() {
        // 헤더 설정 안 함

        Long userId = userHeaderUtil.getUserId();

        assertThat(userId).isNull();
    }

    @Test
    void getUserId_EmptyHeader() {
        request.addHeader("X-User-Id", "");

        Long userId = userHeaderUtil.getUserId();

        assertThat(userId).isNull();
    }

    @Test
    void getUserId_NoRequestAttributes() {
        RequestContextHolder.resetRequestAttributes(); // 요청 컨텍스트 제거

        Long userId = userHeaderUtil.getUserId();

        assertThat(userId).isNull();
    }

    @Test
    void getGuestId_ValidId() {
        String guestUuid = "guest-uuid-1234";
        request.addHeader("X-Guest-Id", guestUuid);

        String result = userHeaderUtil.getGuestId();

        assertThat(result).isEqualTo(guestUuid);
    }

    @Test
    void getGuestId_NullHeader() {
        String result = userHeaderUtil.getGuestId();

        assertThat(result).isNull();
    }

    @Test
    void getGuestId_EmptyHeader() {
        request.addHeader("X-Guest-Id", "");

        String result = userHeaderUtil.getGuestId();

        assertThat(result).isNull();
    }

    @Test
    void getGuestId_NoRequestAttributes() {
        RequestContextHolder.resetRequestAttributes();

        String result = userHeaderUtil.getGuestId();

        assertThat(result).isNull();
    }

    @Test
    void getUserRole_ValidRole() {
        String role = "ROLE_USER";
        request.addHeader("X-User-Role", role);

        String result = userHeaderUtil.getUserRole();

        assertThat(result).isEqualTo(role);
    }

    @Test
    void getUserRole_NullHeader() {
        String result = userHeaderUtil.getUserRole();

        assertThat(result).isNull();
    }

    @Test
    void getUserRole_NoRequestAttributes() {
        RequestContextHolder.resetRequestAttributes();

        String result = userHeaderUtil.getUserRole();

        assertThat(result).isNull();
    }
}