package org.nhnacademy.book2onandonbookservice.aop;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.annotation.AuthCheck;
import org.nhnacademy.book2onandonbookservice.domain.Role;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class AuthCheckAspectTest {
    @Mock
    private UserHeaderUtil util;

    @Mock
    private AuthCheck authCheck;

    @InjectMocks
    private AuthCheckAspect authCheckAspect;

    @Test
    @DisplayName("실패: 유저 역할 정보가 Null인 경우 예외 발생")
    void checkRole_Fail_RoleNull() {
        when(util.getUserRole()).thenReturn(null);

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> {
            authCheckAspect.checkRole(authCheck);
        });
        assertEquals("권한 정보가 없습니다. (로그인 필요)", exception.getMessage());
    }

    @Test
    @DisplayName("실패: 유저 역할 정보가 빈 문자열인 경우 예외 발생")
    void checkRole_Fail_RoleEmpty() {
        when(util.getUserRole()).thenReturn("");

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> {
            authCheckAspect.checkRole(authCheck);
        });

        assertEquals("권한 정보가 없습니다. (로그인 필요)", exception.getMessage());
    }

    @Test
    @DisplayName("성공/ 슈퍼 관리자인 경우 무조건 통과")
    void checkRole_SuperAdmin() {
        String suerAdminRoleName = Role.SUPER_ADMIN.getRoleName();

        when(util.getUserRole()).thenReturn(suerAdminRoleName);

        assertDoesNotThrow(() -> authCheckAspect.checkRole(authCheck));
    }

    @Test
    @DisplayName("성공/ 필요한 권한을 가지고 있는 경우 통과")
    void checkRole_HasPermission() {
        Role userRole = Role.USER;

        when(util.getUserRole()).thenReturn(userRole.getRoleName());

        when(authCheck.value()).thenReturn(new Role[]{Role.USER, Role.SUPER_ADMIN});

        assertDoesNotThrow(() -> authCheckAspect.checkRole(authCheck));
    }

    @Test
    @DisplayName("실패/ 권한이 일치하지 않을때")
    void checkRole_Fail_HasPermission() {
        Role userRole = Role.USER;
        when(util.getUserRole()).thenReturn(userRole.getRoleName());

        when(authCheck.value()).thenReturn(new Role[]{Role.SUPER_ADMIN});

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> {
            authCheckAspect.checkRole(authCheck);
        });

        assertEquals("해당 리소스에 접근할 권한이 없습니다.", exception.getMessage());
    }
}