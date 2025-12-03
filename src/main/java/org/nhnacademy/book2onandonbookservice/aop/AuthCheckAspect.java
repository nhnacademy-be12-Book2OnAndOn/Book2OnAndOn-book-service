package org.nhnacademy.book2onandonbookservice.aop;


import java.util.Arrays;
import lombok.RequiredArgsConstructor;
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
public class AuthCheckAspect {

    private final UserHeaderUtil userHeaderUtil;

    @Before("@annotation(authCheck)")
    public void checkRole(AuthCheck authCheck) throws AccessDeniedException {
        String userRoleStr = userHeaderUtil.getUserRole();

        if (userRoleStr == null || userRoleStr.isEmpty()) {
            throw new AccessDeniedException("권한 정보가 없습니다. (로그인 필요)");
        }
        if (Role.SUPER_ADMIN.getRoleName().equals(userRoleStr)) {
            return;
        }
        boolean hasPermission = Arrays.stream(authCheck.value())
                .anyMatch(allowedRole -> allowedRole.getRoleName().equals(userRoleStr));
        if (!hasPermission) {
            throw new AccessDeniedException("해당 리소스에 접근할 권한이 없습니다.");
        }
    }
}
