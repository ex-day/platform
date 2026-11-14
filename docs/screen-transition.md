# 画面遷移

2026-11-14：[画面一覧](screen-list.md)の作り直し（Issue #135）に合わせて書き直した。新しい画面（S12〜S18）の ID は案。

```mermaid
flowchart TD
TOP[S01 TOP]
SEARCH[S02 探す]
DISC[S03 Discovery のページ]
PLACE[S13 場所のページ]
CONV[S14 会話のページ]
PROJ[S15 企画のページ]
PEDIT[S16 企画の作成・編集]
PLIST[S17 企画の一覧]
DETOUR[S12 寄り道]
MINE[S09 自分の話]
NEW[C27 新しい話を始める<br/>モーダル /posts/new]
LOGIN[C08 ログイン認証ダイアログ]

    TOP -->|企画の枠| PROJ
    TOP -->|企画の枠の「もっと見る」| PLIST
    TOP -->|発見の枠| DISC
    TOP -->|近くの発見の場所の見出し・声を待っている| PLACE
    TOP -->|探しています| CONV
    TOP -->|小さな地図| DETOUR
    TOP -->|「もっと見る」・ヘッダーの検索| SEARCH

    SEARCH --> DISC
    SEARCH --> PLACE
    SEARCH --> PROJ
    SEARCH -->|探していますで絞ったとき| CONV
    SEARCH -->|ここから行ける範囲で絞る| DETOUR

    DETOUR --> PLACE
    DETOUR --> DISC
    DETOUR -->|戻る（遷移元へ。状態を保つ）| TOP
    DETOUR -->|戻る（検索条件を保つ）| SEARCH

    PLACE --> DISC
    PLACE -->|会話・このあたりで探している人がいます| CONV
    PLACE --> PROJ
    PLACE -->|ここから行ける| DETOUR
    DISC -->|関わる場所| PLACE
    DISC -->|由来の会話| CONV
    DISC --> PROJ
    DISC -->|ここから行ける| DETOUR
    CONV -->|採用した場所・入口| PLACE
    CONV -->|成立した Discovery| DISC
    PROJ -->|項目| PLACE
    PROJ -->|項目| DISC
    PROJ -->|企画をきっかけにした会話| CONV
    PROJ -->|作った人が直す| PEDIT
    PLIST --> PROJ
    PLIST -->|作る| PEDIT
    PEDIT -->|未ログインで確定| LOGIN
    PEDIT -->|保存| PROJ

    PLACE -->|話す（入口＝この場所）| NEW
    DISC -->|話す（入口＝この Discovery）| NEW
    PROJ -->|話す（入口＝この企画）| NEW
    TOP -->|ヘッダーの「話す」| NEW
    NEW -->|未ログインで確定| LOGIN
    LOGIN -->|入力内容を引き継いで確定| NEW
    NEW -->|投稿後（場所の共有の誘いのあと）| CONV

    MINE --> CONV
    MINE --> PROJ
    MINE -->|あとでにした場所の共有の誘い| CONV
```

- ヘッダー（C01）の「話す」「寄り道」「検索」は、どの画面からも同じ行き先になる。図では TOP からの線だけを描いた
- 新しい話を始めるのは、ヘッダーの「話す」や各ページの「話す」から開くモーダル[C27（新しい話を始める）](ui/components/C27-post-new.md)で行う。モーダルはどの画面からも開け、URL（`/posts/new`）を直接開いた場合も表示できる。場所・Discovery・企画のページから開いたときは、入口を引き継ぐ。入口から場所が決まる会話は、始まりの時点でその場所を採用する（論理設計 4.3節）
- 投稿の直後、会話に採用した場所がなければ、C27 の中で場所の共有を誘う（DEC-0023）。答えた・断った・あとでのどれでも、S14（会話のページ）へ進む
- 既存の会話への返信は、S14 の入力欄で行い、画面を遷移しない
- 入口は認証状態にかかわらず表示し、未ログインでも入力を始められる。投稿・企画の作成の確定時に C08（ログイン認証ダイアログ）でログインを求め、入力内容はログイン後に引き継ぐ（[DEC-0010](decisions/DEC-0010-duplicate-conversations-and-posting-auth.md) 決定2）
- S18（運営の管理）は、一般の画面からの遷移を持たない。S18 の一覧から、運営の権限で一般の画面（S03・S13・S14・S15）の詳細を開く（[画面一覧](screen-list.md)の「運営の権限」）
- S12（寄り道）から戻るときは、遷移元（TOP・S02・S03・S13）に戻り、遷移元の状態（TOP の表示位置、S02 の検索条件と結果の位置など）を保つ。図では TOP と S02 だけを描いた
- S02 は「探しています」で絞ったときだけ、場所が決まっていない会話・問いを結果に出し、S14 へ進める。それ以外の会話は検索の対象にしない
- v0.3 の「投稿直後に似た Discovery を案内し、器として作られた成立前の Discovery の S03 へ進む」流れは、DEC-0020（会話が器）により使わない。似た会話・Discovery の案内を投稿の直後に出すかは画面の設計で決める
