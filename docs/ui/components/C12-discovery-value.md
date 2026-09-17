# C12 discovery-value
## 機能概要
discoveryに含まれる魅力を紹介するコンポーネント。

## 対象端末
- PC
- モバイル

## 表示項目
| 論理名  | 物理名            | 種別  | 繰り返し | 親   | データ元 | データ項目 | 対象端末    | 備考 |
|---------|-------------------|-------|----------|------|----------|------------|-------------|------|
| subject | discovery_subject | text  | -        | -    | API      | subject    | PC/モバイル |      |
| 価値    | discovery_value   | text  | -        | -    | API      | value      | PC/モバイル |      |
| 写真    | discovery_picture | image | -        | -    | API      | picture    | PC/モバイル |      |
| 条件    | -                 | -     | 0..n     | -    | API      | conditions | PC/モバイル |      |
| 時間    | discovery_time    | text  | -        | 条件 | API      | time       | PC/モバイル |      |
| 季節    | discovery_season  | text  | -        | 条件 | API      | season     | PC/モバイル |      |
| 本文    | discovery_body    | text  | -        | -    | API      | body       | PC/モバイル |      |

## アクション
- 初期表示
    - Discoveryから渡されたvalueを表示する

## 検討事項
なし