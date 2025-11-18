package org.nhnacademy.book2onandonbookservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(
                        AbstractHttpConfigurer::disable) // Cross-Site Request Forgery : 공격자가 사용자 권한 도용하여 특정 웹사이트에 요청을 보내는 공격 근데 우리는 JWT 토큰 사용이라 세션 기반 공격에 대해 비교적 안전
                .httpBasic(
                        AbstractHttpConfigurer::disable) //HTTP Basic 인증 비활성화 : 요청 헤더에 ID와 비밀번호를 암호화하지않고 (Base64 인코딩만 해서) 매번 보내는 방식이라 보안상 취약하고, JWT 토큰을 사용할거라 끔
                .formLogin(
                        AbstractHttpConfigurer::disable) //form 로그인 비활성화 : 프론트에서 별도의 로그인 화면을 만들고 API 요청으로 인증을 처리할거라 필요없음
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS)) // 세션관리 정책 (서버에 세션 생성X, 저장X, JWT 토큰 인증 방식이 무상태인게 핵심 서버가 클라이언트의 상태를 기억X 오직 토큰만 검증하여 인증
                .authorizeHttpRequests(authorize -> authorize.anyRequest()
                        .permitAll()); // 인증을 gateway에서 담당하기때문에 어차피 여기까지 오면 이미 검증된것으로 간주

        return http.build();
    }
}
