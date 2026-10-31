# 認証サービスの比較（Auth0 と Cognito）

最終更新：2026-10-31（#75、#81）

ex-day の認証サービスを選ぶために調べたことのまとめ。**まだ決定ではない。** 決まったら DEC に記録し、この文書はその根拠として残す。議論の経過は #75 のコメントにある。

## いまの見立て（2026-10-31）

- クラウドは AWS が有力（PostgreSQL 18＋PostGIS＋pgRouting が必須のため。#129）
- 認証サービスは、いったん **Auth0 を第一候補** とした（無料の範囲が 25,000 MAU と広い、独自ドメインとパスキーが無料、③の同意の仕組みがそろう）
- その後、費用の見積もり（[cost-model.md](cost-model.md)）で **Auth0 の料金の崖** が分かった。アプリを出すと利用者のほぼ全員が MAU になるので崖が早く来る。**Cognito に傾いている**（人の判断待ち）
- 決める前に、Cognito でも無料のテナントで、パスキー・メールの OTP・ログインの画面を確かめる

## 選ぶための条件

人の判断（#75、2026-10-31）：

- 認証のエンジン（ID の発行、パスワード、トークンの署名など）は自前で作らない。各社のブラウザ向けのライブラリ（AWS Amplify など）を使わずに、標準の OIDC のライブラリでつなぐのはよい
- 認証サービスが何であれ、後から変えられる作りにする
- パスワードを持たない。**パスキーへの対応は必須**（利用者の使いやすさのため）
- API の公開は③（利用者の代わりに、ほかのアプリが ex-day を使う）まで対象にする

どの方式でも満たす要件（#75）：

1. トークンをブラウザの JavaScript から読める場所に置かない（BFF。HttpOnly・Secure・SameSite の cookie、CSRF への手当て）
2. 署名の検証などは標準のライブラリに任せる（Spring Security、Auth.js など）
3. ex-day のユーザー ID を自分で持ち、「どのサービスの誰か」をひも付ける
4. 認証まわりのコードは特に念入りに確かめる
5. 上流のログイン（Google・Apple）の識別子も保存する
6. API の相手の台帳と権限は ex-day 側で持つ

## 候補を Auth0 と Cognito に絞った理由

比べる条件：①パスキー、②独自ドメインを安く使える、③同意の画面つきでほかのアプリに権限を渡せる（取り消しも）、④認証の基盤を自分で動かさない、⑤標準の OIDC でつなげる。

| 候補 | 外した理由 | 確かめ方 |
|---|---|---|
| Clerk | 無料（Hobby、50,000 MRU、独自ドメインあり）では**パスキーが使えない**（Pro $25／月から）。③の仕組みはある。フロントエンド寄りの作り | https://clerk.com/pricing.md 、https://clerk.com/docs/guides/configure/auth-strategies/oauth/scoped-access |
| Microsoft Entra External ID | 独自ドメインには手順上 **Azure Front Door** が要り、その費用がかかる。AWS とも分かれる | https://learn.microsoft.com/en-au/entra/external-id/customers/how-to-custom-url-domain |
| Firebase Auth／Google Identity Platform | ほかのアプリに権限を渡す認可サーバーにならず、③ができない。パスキーへの対応も弱い | 調べ直していない（Maker の知識による見立て） |
| Supabase Auth | Supabase（DB を含む）を使う前提の作りで、ex-day の構成と合わない | 調べ直していない（同上） |
| Keycloak・Zitadel・Ory など（自分で動かす） | 脆弱性への対応と運用を ex-day が負う。④に反する | ― |

前提が変わったら（Clerk の無料にパスキーが入る、など）見直す。

## パスキーと乗り換え

- パスキーは、登録したときのドメイン（RP ID）に紐づく。`xxx.auth0.com`・`xxx.amazoncognito.com` のまま登録させると、乗り換えで全員のパスキーが使えなくなる
- **最初から ex-day の独自ドメイン（例：`auth.ex-day.jp`）で認証を動かす。** Cognito は、独自ドメインと管理型のログイン画面を使うと、RP ID が独自ドメインの FQDN になる（Essentials 以上）
- ドメインを揃えても、登録済みのパスキー（公開鍵）を別のサービスへ移せるかは確かめられていない。移せないものとして考える
- そのため、**パスキー以外のログイン手段（メールの OTP、Google などのソーシャルログイン）を必ず残す。** 乗り換えたときは、ほかの手段で入ってパスキーを登録し直す
- 参考：パスキーの鍵を ex-day 側で持つ方法（Spring Security の WebAuthn）もあるが、管理する範囲が広がるので今は採らない

## パスワードをなくす条件（Auth0 の無料テナントで確かめた）

- パスキーの登録では、メールアドレスを入れたあと、OS やパスワードの管理ツール（iCloud キーチェーン、Google パスワードマネージャー、1Password など）がパスキーを作る。サービスに残るのは公開鍵だけ
- Auth0 では、**メールか SMS の OTP を設定しないと、パスワードでのログインを無効にできない**（"You cannot disable password unless you have `phone_otp` and/or `email_otp` configured."、https://auth0.com/docs/authenticate/database-connections/passwordless-authentication-for-db-connect ）。パスキーだけの認証には対応していない
- 手順：Attributes で Email を足す → Email OTP を許可し「Verify email on sign up」をオン → パスワードの3項目をブロック → 「Support users without a password」をオン
- メールの OTP は、パスキーを作れない端末の人のログイン、パスキーをなくした人の復旧、メールアドレスの確認（`email_verified`）を兼ねる
- **Email OTP には、メールを送る仕組み（SMTP かメール送信のサービス）が要る。** 本番では Amazon SES など（独自ドメインでの SPF・DKIM・DMARC の設定も）
- この機能が正式版か Early Access かは、公式の説明から読み取れなかった
- Cognito では未確認（Essentials でパスキーとメールの OTP が使えるはず。確かめる）

## 比較

| | Auth0 | Cognito（Essentials） |
|---|---|---|
| 無料の範囲 | 25,000 MAU（Free） | 10,000 MAU（期限なし） |
| 無料を超えたあと | Essentials 30,000 MAU で $2,100／月、50,000 で $3,500。それより上は営業 | 超えた分 $0.015／MAU（100,000 MAU で $1,350） |
| 独自ドメイン | 無料で1つ（有効にするにはクレジットカードの登録。請求はされない） | 追加料金なし（証明書は ACM） |
| パスキー | 無料で使える | Essentials 以上で使える |
| パスワードをなくす | メールの OTP が必須（メール送信の準備が要る） | 未確認 |
| MFA | Free では Pro MFA が使えない（要素の内訳は公式に記載なし）。一般の利用者には MFA を求めない方針なので大きな問題ではない | 未確認 |
| 上流（Google・Apple）の識別子（要件5） | 受け取れる見込み | 受け取れる（ユーザー名 `Google_<sub>`、`identities` クレーム） |
| ③の同意の画面 | **ある**（サードパーティのアプリ、user grant） | **ない**。ex-day で同意の画面と台帳を作り、トークン生成前の Lambda で範囲を絞る |
| ③の取り消し | 同意を消すと、その同意で出したトークンがすべて無効になる。利用者向けの画面は管理 API で ex-day が作る | 個別の取り消しには、取り消すトークンそのものが要る（他者のアプリが持つので ex-day から指定できない）。一括の取り消しは ex-day 本体のログインも切れる。台帳と Lambda で発行を止める（拒めるかは未確認） |
| ②サーバー間の API | マシン間トークンの発行数にプランの上限（使い回すので、提携先の数×1日の発行回数で決まる） | M2M には無料の範囲がない |
| クラウドとの関係 | AWS の外の契約先が増える | AWS にまとまる。乗り換えにくくなる |

出典：Auth0 の料金 https://auth0.com/pricing 、https://auth0.com/de/pricing.md 、Auth0 の無料プランの改定 https://auth0.com/blog/auth0-plans-got-an-upgrade （2024-09-24）、取り消し https://auth0.com/docs/secure/tokens/refresh-tokens/revoke-refresh-tokens 、Cognito の料金 https://aws.amazon.com/cognito/pricing/ 、Cognito の取り消し https://docs.aws.amazon.com/cognito/latest/developerguide/token-revocation.html

### どちらでも、ex-day の API 側で入れること

- 取り消しても、発行済みのアクセストークンは有効期限まで使える（署名と期限だけを確かめる検証では有効に見える）
- 外部のアプリに出すアクセストークンの有効期限を短くする（5〜15分程度。設定で持つ）
- 投稿などの重要な操作のたびに、ex-day の台帳で同意が生きているかを確かめる

## MAU と料金（アプリを出す場合）

- MAU は「その月にログインかトークンの更新をした人」の**人数**（https://auth0.com/blog/auth0-monthly-active-user-mau-explained/ ）。同じ人が何回更新しても1人。Cognito も人数で数える
- Web だけなら、読むだけの人はログインしないので、MAU は PV のごく一部（見積もりでは 0.5% と仮置き）。Auth0 の崖は月 約500万PV のあたり
- **アプリはログインしたままが普通なので、その月にアプリを開いた人がほぼ全員 MAU になる。** アプリの利用者が月3万人で、Auth0 は月 約37万円、Cognito は 約5万円（[cost-model.md](cost-model.md) の「アプリの利用者ごとの認証の費用」）
- 乗り換えはパスキーの登録し直しを伴うので、崖が来てから乗り換えるのは避けたい
- Auth0 の崖を抑える手（未確認）：BFF のセッションを長めにして Auth0 への問い合わせを減らす（安全とのつり合い）、スタートアップ向けのプログラム

## 前提になる作業

- **独自ドメインを取ることが、認証（パスキーの紐づけ先）とメール（送信元）の両方の前提になる**（#129 の作業の順番）
- メール送信のサービス（Amazon SES が第一候補。1,000通あたり 約$0.10。最初は送り先が制限された状態で、本番での利用を申請する）

## 次に確かめること

- Cognito の無料のテナントで：パスキーとメールの OTP でパスワードをなくせるか、ログインの画面（managed login）の使い勝手と日本語、Google ログインとのつなぎ方
- ③：Cognito のトークン生成前の Lambda で、トークンの発行を拒めるか
- Auth0 を選ぶ場合：パスワードをなくす機能の提供段階、Free で使える MFA の要素
