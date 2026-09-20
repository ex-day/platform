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
