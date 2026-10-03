package com.example.assetloan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * セキュリティ設定。
 *
 * <p>ポートフォリオでは「認証基盤そのもの」ではなく、貸出ワークフローの業務ルールを見せたいので、
 * 認証は行わず、操作者を {@code X-Employee-Number} ヘッダで受け取る方式にしている。
 * 権限チェック（管理者かどうか）はサービス層で必ず実行する。
 *
 * <p>本番で使う場合は、この設定を OIDC（Spring Security の oauth2ResourceServer）に置き換え、
 * {@code CurrentEmployeeResolver} がヘッダではなく認証トークンから社員を引くようにすればよい。
 * 置き換え対象がこの 2 か所に閉じているのが狙い。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // デモ用の REST API のみなので CSRF トークンは使わない（Cookie 認証をしていないため）
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }
}
