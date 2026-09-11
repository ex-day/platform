# S01 TOP ワイヤーフレーム生成

## 目的
S01 TOP画面定義からPC版ワイヤーフレームを生成できるか検証する。

## 使用ツール
Figma AI

## 入力
- ex-day/platform/docs/ui/

## Prompt
ui/screens/S01-top.md1を元にワイヤーフレームを作ってください。
表示項目にあるCXX:で始まるものはui/componentsにあるCxx-で始まるファイルに定義があるものとします。

## 結果
- PC版ワイヤーフレームを生成できた
- Component IDも画面へ反映された
- 「近くのDiscovery」
- 「今週よく見られている」
- 「新着のDiscovery」
  など、定義から具体的なUIが提案された

## スクリーンショット
[結果](evidence/S01-Top-Wireframes.dc.html)

## 得られた課題・気づき
- 「今週よく見られてい」の判定基準が必要
- Sectionの構成について追加検討が必要

## 判断
- PC版ワイヤーフレーム生成としては成功
- AIが補完した仕様をそのまま採用せず、要求・機能定義へフィードバックする