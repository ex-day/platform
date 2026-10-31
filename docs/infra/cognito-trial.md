# Cognito を試す手順

最終更新：2026-10-31（#75）

認証サービスを Cognito Essentials に決める前に（[auth-service-comparison.md](auth-service-comparison.md)）、ex-day の条件を満たすかを、実際の画面と標準のライブラリで確かめる。結果は下の「記録」に書き、#75 にも残す。

## 何を確かめるか

| # | 確かめること | なぜ |
|---|---|---|
| A | **ログインの画面（managed login）が、ex-day で使える出来か**（日本語、見た目、流れ） | 以前の Cognito の画面（hosted UI）は使いにくく、自前の画面を作った結果、標準のライブラリを使えなかった経験がある（人） |
| B | パスキーとメールの OTP だけで、パスワードを持たずに登録・ログインできるか | パスワードを持たない方針（#75） |
| C | Google でのログインと、上流の識別子（`Google_<sub>`、`identities`）を受け取れるか | 要件5 |
| D | **標準の OIDC のライブラリ（Spring Security など）で、そのままつなげるか** | 要件2。自前の画面にすると、ここが崩れる |
| E | 外部の事業者からのデータ登録（M2M）を、提供先ごとの App Client とスコープで作れるか | ex-day が先に必要とする API の公開 |
| F | パスキーをどこで登録できるか（ログインの画面の中だけか、ex-day の設定の画面からもできるか） | パスキーは登録したドメインに紐づく。ex-day の画面（例：`www.ex-day.jp`）とログインの画面（例：`auth.ex-day.jp`）でドメインが違う |

### 前もって分かっていること（公式、2026-10-31）

https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-managed-login.html より：

- 今のログインの画面は **managed login**（2024年に出た新しい版）。以前の画面は「hosted UI（classic）」として別に残っている。**パスキーは managed login でしか使えない**
- managed login は **Essentials 以上**で、ノーコードの見た目の編集（branding editor：ロゴ、背景、色、ダークモードなど）が使える。HTML・CSS・JavaScript を自由に書けるわけではない
- **日本語に対応**（`lang=ja` を付ける。一度付けると cookie で覚える）。Essentials 以上で、managed login の見た目を使っている場合だけ
- 利用規約とプライバシーポリシーへのリンクを、登録の画面に出せる
- `/oauth2/authorize` に `identity_provider=Google` を付けると、Cognito の画面を出さずに Google のログインへ直接移る
- managed login ができるのは、登録・ログイン・MFA・パスキーの登録まで。属性の変更などの「プロフィールの管理」は、アプリの側で作る
- ログインの画面の cookie は1時間。それを過ぎたら、もう一度ログインする

https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-authentication-flow-methods.html より：

- パスワードなし（passwordless）を有効にすると、**利用者はパスワードなしで登録でき**、ログインのときにパスワードなしの方法を選ぶ。ただし、パスワードを持たない利用者も、API（`ChangePassword`）で前のパスワードなしにパスワードを設定できる（ex-day はこの操作を画面に出さない）
- パスキーは、ほかに少なくとも1つの方法と一緒でないと有効にできない（メールの OTP を組み合わせる）
- managed login は、登録の直後にパスキーの登録を勧める。ログインの画面のセッション（cookie、1時間）がある間は、パスキーの登録用のページへ送って足してもらうこともできる（F：ex-day の設定の画面からは、このページへ送る形になる見込み）
- パスキーの RP ID は、Cognito のドメイン、独自ドメイン、第三者のドメイン（managed login を使わず、API を直接呼ぶ場合）のいずれか
- メールのコードで入ると、メールアドレスが確認済みになる

### 試した結果（途中）

- 2026-10-31：Essentials で、選択ベースのサインインにパスキーを足すと「パスワードなし：アクティブ」になった。**「パスワード」は有効な選択肢から外せない**（コンソールで固定）。パスワードなしで登録できるか、登録・ログインの画面でパスワードが見えるかは、手順5-1で確かめる

**自前の画面の代わりになる形（hybrid）**：ex-day のログインのモーダル（C08）に「Google で続ける」「Apple で続ける」「メール・パスキーで続ける」を置き、Google・Apple は `identity_provider` で直接、メール・パスキーだけ managed login に任せる。どれも OIDC の認可コードのフローなので、標準のライブラリが使える。以前の経験でライブラリが使えなかったのは、画面を全部自前にして Cognito の API（`InitiateAuth` など）を直接呼んだためと思われる。この形なら、画面の大半は ex-day のものにしつつ、そうならずに済む見込み。

## 準備

1. **AWS のアカウント**（なければ作る）。リージョンは東京（ap-northeast-1）
2. **予算の通知**：AWS Budgets で月 $5 の予算と、超えたらメールの通知を作る（試すだけなら費用はほぼ0円。M2M のトークン発行は1回 $0.00225）
3. **Google のログイン用の資格情報**：Google Cloud Console で OAuth クライアント（ウェブ アプリケーション）を作る。承認済みのリダイレクト URI は、手順2で決まる Cognito のドメインの `https://<prefix>.auth.ap-northeast-1.amazoncognito.com/oauth2/idpresponse`
4. 試す端末：Mac（Safari・Chrome）、iPhone、できれば Android と、パスキーを使っていない端末（パスキーを作れない場合の確認）

独自ドメインはまだないので、Cognito のドメイン（`<prefix>.auth.ap-northeast-1.amazoncognito.com`）で試す。**ここで登録したパスキーは本番では使えない**（ドメインが変わるため）。試しのものと割り切る。

## 手順

### 1. ユーザープールを作る

1. Cognito のコンソール →「ユーザープールを作成」
2. アプリの種類：**従来のウェブアプリケーション**（サーバー側で秘密を持てる形。ex-day の BFF に合わせる）
3. サインインの識別子：**メールアドレス**
4. 作ったら「Settings」→ 機能プラン（feature plan）が **Essentials** になっているか確かめる
5. 「Authentication methods」→ サインインの選択肢（choice-based sign-in）で、最初の要素に **パスキー** と **メールのワンタイムコード** を許可する。**パスワードを外せるか**を確かめる（B）
6. パスキーの設定：依存するパーティー（RP）の ID はユーザープールのドメイン。ユーザー検証（生体認証など）は「必須」か「推奨」かを確かめて記録する
7. メールの送信：試しは **Cognito の既定のメール送信**（1日の通数に上限がある）でよい。本番は SES にする

### 2. ドメインと見た目

1. 「Domain」→ Cognito のドメイン（prefix）を作る。**Branding version は Managed login** を選ぶ
2. 「Managed login」→ branding editor で、ロゴ・色を ex-day 風に変えてみる（A）
3. 利用規約・プライバシーポリシーの仮の URL を設定して、登録の画面にリンクが出るか見る

### 3. App Client（ex-day の画面用）

1. 「App clients」→ 作る（クライアントシークレットあり）
2. コールバックの URL：`http://localhost:3000/login/oauth2/code/cognito`（あとで試すアプリに合わせる）。サインアウト後の URL：`http://localhost:3000/`
3. OAuth の付与：**認可コード**。スコープ：`openid`、`email`、`profile`
4. 「View login pages」で画面を開き、URL に `&lang=ja` を足して日本語になるか見る

### 4. Google をつなぐ

1. 「Social and external providers」→ Google を足す（準備3の資格情報）。属性の対応で `email` をつなぐ
2. App Client の「Identity providers」で Google を有効にする
3. `/oauth2/authorize?...&identity_provider=Google` で、Cognito の画面を飛ばして Google に直接移るか見る（hybrid の形）

### 5. 画面で試す（A・B・C・F）

| # | やること | 見ること |
|---|---|---|
| 5-1 | メールアドレスで新しく登録する | パスワードを求められないか。コードのメールが届くか。パスキーの登録を勧められるか |
| 5-2 | パスキーを登録して、ログアウト → パスキーでログイン | Mac・iPhone で、パスワードの管理ツール（iCloud キーチェーン、1Password など）が出て、そのまま入れるか |
| 5-3 | パスキーのない端末でログイン | メールのコードで入れるか（パスキーをなくした人の復旧も同じ道） |
| 5-4 | メールのコードで入ったあと、新しい端末でパスキーを足す | ログインの画面の中で足せるか。ex-day の設定の画面から足す方法があるか（F） |
| 5-5 | Google でログイン | ユーザー名が `Google_<sub>` になるか。ID トークンに `identities` があるか（C） |
| 5-6 | 同じメールアドレスで、メールの登録と Google の両方を使う | 別々のユーザーになるか（つなぐには Lambda などが要るか） |
| 5-7 | 全体を日本語（`lang=ja`）で通す | 文言が自然か、読みにくいところはないか（A） |
| 5-8 | スマホの画面で通す | 見た目・押しやすさ（A） |

### 6. 標準のライブラリでつなぐ（D）

最小の Spring Boot のアプリ（Spring Security の OAuth2 Client）を作り、次を確かめる。アプリは Claude Code で用意できる。

- `issuer-uri`（`https://cognito-idp.ap-northeast-1.amazonaws.com/<ユーザープールID>`）を設定するだけで、ログイン → コールバック → ユーザー情報まで動くか
- トークンをブラウザに渡さず、サーバー側のセッション（HttpOnly の cookie）で持てるか（要件1）
- ログアウト：Cognito は OIDC の標準のログアウトの入口（`end_session_endpoint`）を公開していないとみられ、`/logout?client_id=...&logout_uri=...` を呼ぶ形になる。Spring Security でどこまで標準のまま書けるか（確かめる）

### 7. M2M（E）

1. 「Resource servers」→ 作る（例：識別子 `https://api.ex-day.example`、スコープ `events:write`、`events:read`）
2. 提供先の App Client を作る：「Machine-to-machine application」、クライアントシークレットあり、許可するスコープは `events:write` だけ
3. トークンを取る：

   ```bash
   curl -s -u '<client_id>:<client_secret>' \
     -d 'grant_type=client_credentials&scope=https://api.ex-day.example/events:write' \
     https://<prefix>.auth.ap-northeast-1.amazoncognito.com/oauth2/token
   ```

4. アクセストークンの中身（`client_id`、`scope`、有効期限）を見る。有効期限を変えられる幅（App Client の設定）を記録する
5. 2つ目の提供先を作り、App Client を止める・secret を入れ替える操作が、提供先ごとにできるか見る

## 判断の目安

- **A・B・D が満たせれば、Cognito Essentials で進める**（DEC に記録する）
- A が足りない（画面が使いにくい）場合：hybrid の形（Google・Apple は ex-day のボタンから直接、メール・パスキーだけ managed login）で足りるかを見る。それでも足りなければ、Auth0 と比べ直す（Cognito の API を直接呼ぶ自前の画面には戻らない。要件2 が崩れるため）
- B が満たせない（パスワードが残る）場合：パスワードでのログインを画面に出さずに済むかを見て、#75 で判断する
- F で、ex-day の設定の画面からパスキーを足せない場合：パスキーの登録はログインの画面の中だけ、という前提で S08 を考える

## 後片付け

- 試しのユーザープールは、本番用とは別に作り直す（本番は独自ドメインで作る）。試しのものは記録を書いたら消してよい
- Google Cloud の試しの OAuth クライアントも消すか、本番用に作り直す

## 記録（試したら書く）

| # | 結果 | メモ |
|---|---|---|
| A | | |
| B | | |
| C | | |
| D | | |
| E | | |
| F | | |
