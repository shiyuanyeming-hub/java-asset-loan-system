package com.example.assetloan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 社内備品の貸出管理システム。
 *
 * <p>社員は備品の検索と貸出申請を行い、管理者が承認・却下・返却処理を行う。
 * 貸出中の備品は再申請できず、状態遷移（申請→承認→返却）はサービス層で検証する。
 */
@SpringBootApplication
public class AssetLoanApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssetLoanApplication.class, args);
    }
}
