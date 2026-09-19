# C12 discovery-value
## 機能概要
Discoveryに含まれるValueを紹介するコンポーネント。S03では現在成立しないValueを「その他の魅力」として表示する。Recommendationの評価やHero内での選択切替は担わない。

## 対象端末
- PC
- モバイル

## 表示項目
| 論理名  | 物理名            | 種別  | 繰り返し | 親   | データ元 | データ項目 | 対象端末    | 備考 |
|---------|-------------------|-------|----------|------|----------|------------|-------------|------|
| 価値の対象 | discovery_subject | text  | -        | -    | API      | subject    | PC/モバイル | 「城跡」「散策」等、何についての魅力かを伝える名称。Subjectの内部属性一覧ではない |
| 価値    | discovery_value   | text  | -        | -    | API      | value      | PC/モバイル |      |
| 写真    | discovery_picture | image | 0..1        | -    | API      | picture    | PC/モバイル |      |
| 楽しめる条件の説明 | -          | -     | 0..n     | -    | API      | conditions | PC/モバイル | 時間・季節等をユーザーに伝える説明のまとまり。内部のCondition構造を直接表示しない |
| 時間    | discovery_time    | text  | -        | 楽しめる条件の説明 | API      | time       | PC/モバイル |      |
| 季節    | discovery_season  | text  | -        | 楽しめる条件の説明 | API      | season     | PC/モバイル |      |
| 本文    | discovery_body    | text  | -        | -    | API      | body       | PC/モバイル |      |

時間・季節はConditionの表示例であり、網羅的な構造ではない。場所・Subject・Discovery状態等の条件も説明できるようにし、具体的な項目構造は後続設計で定める。写真は任意とし、ない場合もValueの説明は表示する。

## アクション
- 初期表示
    - Discoveryから渡されたvalueを表示する

## 検討事項
- 現在成立しない条件の説明方法、および時間・季節以外のConditionの表示
- 映像等のMedia対応とHero内での再利用の要否
- Conditionの詳細構造はDomain／Entity側の検討に従う
