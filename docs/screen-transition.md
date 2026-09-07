```mermaid
flowchart TD
TOP[TOP]
LIST[Discovery一覧]
DETAIL[Discovery詳細]
ADD[知識追加]
POST[知識・疑問登録]
LOGIN{{ログインダイアログ}}
SIGNUP[新規ユーザー登録]

    TOP --> LIST
    TOP --> DETAIL
    LIST --> DETAIL

    DETAIL -->|知識を追加| AUTH{ログイン済み？}
    AUTH -->|Yes| ADD
    AUTH -->|No| LOGIN
    LOGIN -->|認証成功| ADD
    LOGIN -->|新規登録| SIGNUP
```
