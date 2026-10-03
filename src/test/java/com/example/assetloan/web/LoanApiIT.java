package com.example.assetloan.web;

import com.example.assetloan.domain.Asset;
import com.example.assetloan.domain.AssetCategory;
import com.example.assetloan.domain.AssetStatus;
import com.example.assetloan.domain.Department;
import com.example.assetloan.domain.Employee;
import com.example.assetloan.domain.Role;
import com.example.assetloan.repository.AssetCategoryRepository;
import com.example.assetloan.repository.AssetRepository;
import com.example.assetloan.repository.DepartmentRepository;
import com.example.assetloan.repository.EmployeeRepository;
import com.example.assetloan.repository.LoanHistoryRepository;
import com.example.assetloan.repository.LoanRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST API の結合テスト（HTTP リクエスト → レスポンスまでを通す）。
 *
 * <p>README に載せている curl の例と同じ流れを、テストとして自動検証している:
 * 備品一覧 → 貸出申請 → 承認待ち一覧 → 承認 → 貸出中を確認 → 返却。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanApiIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private LoanRepository loanRepository;
    @Autowired
    private LoanHistoryRepository historyRepository;
    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private AssetCategoryRepository categoryRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private DepartmentRepository departmentRepository;

    private Asset asset;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        loanRepository.deleteAll();
        assetRepository.deleteAll();
        employeeRepository.deleteAll();
        categoryRepository.deleteAll();
        departmentRepository.deleteAll();

        Department department = departmentRepository.save(new Department("SALES", "営業部"));
        employeeRepository.save(new Employee("E1001", "佐藤 花子", "hanako@example.com", department, Role.EMPLOYEE));
        employeeRepository.save(new Employee("E9001", "管理者 太郎", "admin@example.com", department, Role.ADMIN));
        AssetCategory category = categoryRepository.save(new AssetCategory("ノートPC", 14));
        asset = assetRepository.save(new Asset("PC-0001", "ThinkPad X1", category, "Lenovo", "X1 Carbon", null));
        assetRepository.save(new Asset("MON-0001", "27インチ モニター",
                categoryRepository.save(new AssetCategory("モニター", 30)), "Dell", "U2723QE", null));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String employeeNumber) {
        return builder.header(CurrentEmployeeResolver.HEADER, employeeNumber);
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("備品一覧: キーワードで絞り込める")
    void searchAssets() throws Exception {
        mockMvc.perform(get("/api/assets").param("keyword", "thinkpad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].managementNumber").value("PC-0001"))
                .andExpect(jsonPath("$.items[0].lendable").value(true));
    }

    @Test
    @DisplayName("貸出申請 → 承認 → 返却 の流れが API で通る")
    void loanLifecycleThroughApi() throws Exception {
        // 1. 申請
        MvcResult created = mockMvc.perform(as(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\": %d}".formatted(asset.getId())), "E1001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.statusLabel").value("申請中"))
                .andReturn();
        long loanId = readJson(created).get("id").asLong();

        // 2. 承認待ち一覧に出る（管理者）
        mockMvc.perform(as(get("/api/loans/pending"), "E9001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(loanId));

        // 3. 一般社員は承認できない（403）
        mockMvc.perform(as(post("/api/loans/%d/approve".formatted(loanId)), "E1001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_OPERATION"));

        // 4. 管理者が承認（返却期限 14 日）
        mockMvc.perform(as(post("/api/loans/%d/approve".formatted(loanId)), "E9001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.statusLabel").value("貸出中"));

        // 5. 備品は貸出中になっている
        mockMvc.perform(get("/api/assets/%d".formatted(asset.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOANED"))
                .andExpect(jsonPath("$.lendable").value(false))
                .andExpect(jsonPath("$.currentLoan.requesterName").value("佐藤 花子"));

        // 6. 貸出中の備品に再申請すると 409
        mockMvc.perform(as(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\": %d}".formatted(asset.getId())), "E1001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // 7. 返却すると貸出可能に戻る
        mockMvc.perform(as(post("/api/loans/%d/return".formatted(loanId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"キズなし\"}"), "E9001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"));

        mockMvc.perform(get("/api/assets/%d".formatted(asset.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.lendable").value(true));

        // 8. 履歴が 3 件（申請・承認・返却）
        mockMvc.perform(as(get("/api/loans/%d/history".formatted(loanId)), "E1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].action").value("REQUEST"))
                .andExpect(jsonPath("$[2].action").value("RETURN"));
    }

    @Test
    @DisplayName("操作者ヘッダが無いと 403")
    void missingActorHeaderIsForbidden() throws Exception {
        mockMvc.perform(get("/api/loans/my"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_OPERATION"));
    }

    @Test
    @DisplayName("存在しない備品は 404")
    void unknownAssetIsNotFound() throws Exception {
        mockMvc.perform(get("/api/assets/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("入力値が不正なときは 400 と項目別メッセージ")
    void validationError() throws Exception {
        mockMvc.perform(as(post("/api/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"managementNumber\": \"\", \"name\": \"\"}"), "E9001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.managementNumber").exists());
    }

    @Test
    @DisplayName("却下: 理由が無いと 400、理由があれば REJECTED になり備品は貸出可能のまま")
    void rejectFlow() throws Exception {
        MvcResult created = mockMvc.perform(as(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\": %d}".formatted(asset.getId())), "E1001"))
                .andExpect(status().isCreated())
                .andReturn();
        long loanId = readJson(created).get("id").asLong();

        mockMvc.perform(as(post("/api/loans/%d/reject".formatted(loanId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"\"}"), "E9001"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(as(post("/api/loans/%d/reject".formatted(loanId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"同じ日程で他部署が予約済み\"}"), "E9001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("同じ日程で他部署が予約済み"));

        mockMvc.perform(get("/api/assets/%d".formatted(asset.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("管理者は備品を登録でき、管理番号の重複は 409")
    void createAssetAndDuplicate() throws Exception {
        String body = """
                {"managementNumber": "TAB-0001", "name": "iPad Pro", "categoryId": %d,
                 "manufacturer": "Apple", "model": "M4", "purchasedOn": "2026-04-01"}
                """.formatted(categoryRepository.findByName("ノートPC").orElseThrow().getId());

        mockMvc.perform(as(post("/api/assets").contentType(MediaType.APPLICATION_JSON).content(body), "E9001"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.managementNumber").value("TAB-0001"));

        mockMvc.perform(as(post("/api/assets").contentType(MediaType.APPLICATION_JSON).content(body), "E9001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));
    }

    @Test
    @DisplayName("統計 API が状態別の件数を返す")
    void stats() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/assets/stats"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode stats = readJson(result);
        assertThat(stats.get("total").asLong()).isEqualTo(2);
        assertThat(stats.get("available").asLong()).isEqualTo(2);
        assertThat(stats.get("requestedLoans").asLong()).isZero();
    }
}
