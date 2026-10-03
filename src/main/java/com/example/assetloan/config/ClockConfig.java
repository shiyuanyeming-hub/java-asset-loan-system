package com.example.assetloan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 時刻の取得を {@link Clock} 経由に統一するための設定。
 *
 * <p>返却期限・延滞判定はテストで日付を固定したいので、
 * {@code LocalDate.now()} を直接呼ばずにこの Bean を注入して使う。
 */
@Configuration
public class ClockConfig {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Tokyo");

    @Bean
    public Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
