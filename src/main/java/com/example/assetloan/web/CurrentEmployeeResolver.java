package com.example.assetloan.web;

import com.example.assetloan.domain.Employee;
import com.example.assetloan.exception.ForbiddenOperationException;
import com.example.assetloan.service.CatalogService;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 「誰として操作しているか」を解決する。
 *
 * <p>本システムはデモ用に認証を省略し、{@code X-Employee-Number} ヘッダで社員を特定する。
 * 権限チェック（管理者かどうか）はサービス層で必ず行うので、ヘッダを偽装しても
 * 管理者しか実行できない操作は実行できない。
 *
 * <p><b>ヘッダの社員番号が未登録の場合は、その場で登録する</b>（開発・デモ用の割り切り）。
 * こうしないと「最初の管理者を誰が作るのか」という鶏卵問題で何も操作できなくなる。
 * 実運用では SSO（OIDC など）に置き換え、社員は人事システムから同期する想定で、
 * 置き換え箇所がこのクラスだけになるようにしている。
 */
@Component
public class CurrentEmployeeResolver implements HandlerMethodArgumentResolver {

    /** 社員を特定するヘッダ名。 */
    public static final String HEADER = "X-Employee-Number";

    private final CatalogService catalogService;

    public CurrentEmployeeResolver(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(Employee.class)
                && parameter.hasParameterAnnotation(CurrentEmployee.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String employeeNumber = webRequest.getHeader(HEADER);
        if (employeeNumber == null || employeeNumber.isBlank()) {
            throw new ForbiddenOperationException(HEADER + " ヘッダで操作者を指定してください（例: E1001）");
        }
        return catalogService.findOrCreateByEmployeeNumber(employeeNumber.trim());
    }
}
