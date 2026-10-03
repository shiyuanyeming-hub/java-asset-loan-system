package com.example.assetloan.config;

import com.example.assetloan.web.CurrentEmployeeResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * MVC の設定。{@code @CurrentEmployee} を解決するリゾルバを登録する。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentEmployeeResolver currentEmployeeResolver;

    public WebConfig(CurrentEmployeeResolver currentEmployeeResolver) {
        this.currentEmployeeResolver = currentEmployeeResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentEmployeeResolver);
    }
}
