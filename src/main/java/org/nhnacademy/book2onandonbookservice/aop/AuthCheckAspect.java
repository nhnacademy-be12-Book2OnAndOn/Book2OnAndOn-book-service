package org.nhnacademy.book2onandonbookservice.aop;


import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.nhnacademy.book2onandonbookservice.annotation.AuthCheck;
import org.nhnacademy.book2onandonbookservice.domain.Role;
import org.nhnacademy.book2onandonbookservice.util.UserHeaderUtil;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthCheckAspect {

    private final UserHeaderUtil userHeaderUtil;

    @Before("@annotation(authCheck)")
    public void checkRole(AuthCheck authCheck) throws AccessDeniedException {
        String userRoleStr = userHeaderUtil.getUserRole();

        if (userRoleStr == null || userRoleStr.isEmpty()) {
            throw new AccessDeniedException("권한 정보가 없습니다. (로그인 필요)");
        }
        String[] userRoles = userRoleStr.split(",");
        boolean isSuperAdmin = Arrays.stream(userRoles)
                .map(String::trim)
                .anyMatch(role -> Role.SUPER_ADMIN.getRoleName().equals(role));

        if (isSuperAdmin) {
            return;
        }
        if (Role.SUPER_ADMIN.getRoleName().equals(userRoleStr)) {
            return;
        }
        boolean hasPermission = Arrays.stream(authCheck.value()) // 필요한 권한 목록 순회
                .anyMatch(requiredRole ->
                        Arrays.stream(userRoles) // 사용자의 보유 권한 목록 순회
                                .map(String::trim)
                                .anyMatch(userRole -> userRole.equals(requiredRole.getRoleName()))
                );

        if (!hasPermission) {
            // 디버깅을 위해 로그를 남겨두면 좋습니다.
            log.error("권한 거부됨. 사용자 보유 권한: [{}], 필요 권한: [{}]", userRoleStr, Arrays.toString(authCheck.value()));
            throw new AccessDeniedException("해당 리소스에 접근할 권한이 없습니다.");
        }
    }
}
