-- 初期管理者の投入
--
-- このシステムはデモ用に認証を省略しており、操作者は X-Employee-Number ヘッダで指定する。
-- ヘッダの社員番号が未登録の場合は自動的に登録する仕組みにしてあるが、
-- それだと表示名が仮の名前になってしまうため、最初の管理者だけはここで用意しておく。
--
-- パスワードは持たない（認証を行わないデモのため）。実運用では SSO に置き換える。

INSERT INTO department (id, code, name) VALUES (1, 'ADMIN', '情報システム部');

INSERT INTO employee (id, employee_number, name, email, department_id, role, active)
VALUES (1, 'E9001', '管理者 太郎', 'admin.taro@example.com', 1, 'ADMIN', TRUE);
