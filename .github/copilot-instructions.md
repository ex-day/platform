# Copilot 向けの指示

このファイルは、GitHub Copilot のコードレビューと Copilot Chat が読む。ルールの正はルートの `AGENTS.md`、対象ディレクトリの `AGENTS.md`、`docs/development/ai-collaboration.md` にある。ここでは、それらに沿って**レビューする際の観点**だけを短く書く。同じ内容を重ねて書かない。

- レビューのコメントは日本語で書く。
- 判断に迷う場合や、ここに書いていない事項は、上記の資料を優先する。資料間に矛盾がある場合は、そのことをコメントに書き、人間の判断を待つ（`ai-collaboration.md` §5）。

## 参照する資料

- ルートの [`AGENTS.md`](../AGENTS.md)（PR・commit の書き方を含む）
- 対象ディレクトリの `AGENTS.md`（例：[`apps/web/AGENTS.md`](../apps/web/AGENTS.md)、[`apps/api/AGENTS.md`](../apps/api/AGENTS.md)）
- [`docs/development/ai-collaboration.md`](../docs/development/ai-collaboration.md)
- PR テンプレート：[`.github/pull_request_template.md`](./pull_request_template.md)

## レビューの観点

### PR のタイトル・本文

ルートの `AGENTS.md`「PR・commit の書き方」と `ai-collaboration.md` §7 に沿っているかを見る。

- タイトルが日本語で、`種類(範囲): 内容 (#Issue番号)` の形になっているか。
- 本文に §7 の記載事項があるか。特に：
  - 対象 Issue、解決する問題と変更後の動作・運用。
  - 変更した成果物と主要な判断理由、参照した設計資料・基準 commit。
  - 実施した検証と結果。**未実施の検証を成功と書いていないか**（未実施はその理由）。
  - Maker／Reviewer の担当と、レビュー対象 commit、レビュー結果または待ち状況。Maker には担当した AI（分かればモデル名）が書かれているか。
  - 判断待ち・既知の問題・後続 Issue。ない場合は「なし」と書かれているか。
- Human Intervention（人間の代行・直接修正・方針変更等）があった場合、`ai-collaboration.md` §2 の形式で PR に記録されているか。

### 変更範囲

- Issue の範囲を越えた変更や、関係のない変更が混ざっていないか（`ai-collaboration.md` §3・§7）。
- commit に別作業の未追跡ファイルや未コミットの変更が紛れ込んでいないか（ルート `AGENTS.md`、`ai-collaboration.md` §7「作業場所の分離」）。

### AI が勝手に決めていないか

`ai-collaboration.md` §3 の「AI だけで確定しない事項」を、Maker が勝手に決めていないかを見る。該当しそうな判断は、判断待ちとして記録されているかを確認する。

- 新しい仕様、ドメイン概念、状態の種類・遷移、データの意味や成立条件。
- 重要な UX、画面の役割、利用者の行動や表示内容を変える判断。
- 設計書や Issue の不足・矛盾、複数の有力な選択肢があり目的や体験に影響する判断。
- 作業範囲・完了条件の変更や、未採用の提案を前提とした実装。

### 対象ディレクトリのルール

- 変更がある対象ディレクトリの `AGENTS.md` に沿っているか。
- 特に `apps/api` の変更では、[`apps/api/AGENTS.md`](../apps/api/AGENTS.md) の書き方（Java／Spring Boot、`JdbcClient`、SQL の置き場所とパラメータ渡し、Testcontainers 等）に沿っているか。

## コメントの書き方

- 指摘は、**既存の仕様に対する不具合**と、**新しい提案・人間への質問**を区別して書く（`ai-collaboration.md` §2 の Reviewer の責務）。
- 指摘には根拠（該当ファイル・行、参照する資料の節）と影響を添える。
- Reviewer は仕様決定者ではないため、レビュー内で新しい仕様を確定しない。仕様や重要な UX に関わる提案は、人間の判断を促す形にする。
