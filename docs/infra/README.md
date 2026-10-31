# インフラ・費用の調査

ex-day を動かすための、クラウド・認証サービス・費用の調査をまとめる。Issue のコメントは議論の経過、このフォルダは調べた結果のまとめとして使い、Issue を閉じたあとも、ここを見れば分かるようにする。決まったことは `docs/decisions/` の DEC に記録し、ここの文書はその根拠として残す。

| 文書 | 内容 | 関係 |
|---|---|---|
| [cost-model.md](cost-model.md) | 月間PVごとの費用（AWS・認証・AI の解析）と広告収入、損益分岐 | #81 |
| [cost_model.py](cost_model.py) | 上の見積もりの計算。前提と単価を直して計算し直す | #81 |
| [auth-service-comparison.md](auth-service-comparison.md) | 認証サービス（Auth0・Cognito）の比較。パスキー、パスワードをなくす条件、M2M と③、MAU と料金 | #75 |
| [cognito-trial.md](cognito-trial.md) | Cognito を試す手順と、結果の記録 | #75 |

## 更新のしかた

- 単価・前提・構成が変わったら、文書を直し、冒頭の「最終更新」の日付と Issue を書き換える
- 費用は `python3 docs/infra/cost_model.py` を実行し、`cost-model.md` の表を出力で置き換える
- 未確認の値は「未確認」と書き、確かめたら出典と日付を足す
