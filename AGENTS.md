# ex-day AI作業ルール

このリポジトリで作業するAIは、作業開始前に次を守る。

- Issue本文・コメント、関連設計資料、対象ディレクトリにある追加の`AGENTS.md`を確認する。
- AI協働開発の詳細は`docs/development/ai-collaboration.md`を参照する。
- Gitリポジトリを変更する作業は、原則としてIssue単位のbranchとGit worktreeで行う。
- 人間がIDE等で使用している通常のcheckout上でbranchを切り替えない。
- 作業開始前に現在branch、未コミットの変更、未追跡ファイルを確認し、別作業の変更を編集・移動・削除・commitへ含めない。
- 共有checkoutや他Issueの変更と混在している場合は、作業を開始せずIssue用worktreeへ分離する。安全に分離できない場合は人間へ確認する。
- `main`へ直接変更・commit・pushせず、Issue用branchからPRを作成する。最終mergeは人間が行う。
- 判断待ち、人間の介入、レビュー、後続Issueの扱いは`docs/development/ai-collaboration.md`に従って記録する。

下位ディレクトリの`AGENTS.md`には、その範囲に固有の追加ルールがある。ルートの本書と併せて適用し、内容が矛盾する場合は作業を止めて人間へ確認する。

## PR・commit の書き方

PR の本文は作業の途中で作られ、後から直せない場合がある。最初の commit・PR を作る前に本節を読むこと。詳細と正の定義は`docs/development/ai-collaboration.md` §7 にある。本節と §7 が食い違う場合は §7 を優先し、本節の修正を人間へ確認する。

- **PR のタイトル**：日本語で書く。`種類(範囲): 内容 (#Issue番号)`の形にする（例：`docs(db): db/README.md に DB への入り方を足す (#94)`）。範囲を特定しにくい場合は`(範囲)`を省略できる（例：`docs: AGENTS.md に PR・commit の書き方を足す (#97)`）。
- **commit メッセージ**：日本語で書き、PR のタイトルと同じ形にする。変更の目的が分かる内容にする。
- **PR の本文**：次の記載事項を必ず含める（§7 準拠）。
  - 対象 Issue、解決する問題と変更後の動作・運用。
  - 変更した成果物と主要な判断理由、参照した設計資料・基準 commit。
  - 実施した検証と結果。未実施の確認や制約はその理由。**未実施の検証を成功と書かない。**
  - Maker／Reviewer の担当とレビュー対象 commit、レビュー結果または待ち状況。Maker には担当した AI（分かればモデル名）を書く。
  - 判断待ち・既知の問題・後続 Issue。ない場合もその旨を書く。
- Human Intervention（人間の代行・修正等）があった場合は、§2 の Human Intervention Log に従って PR へ記録する。

人が PR を作るときにも使える雛形として`.github/pull_request_template.md`を用意している。
