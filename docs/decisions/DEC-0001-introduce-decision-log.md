# DEC-0001: Decision Logを導入する

## Status
Accepted

## Context
ChatGPTで壁打ちした内容をWork/Codexへ引き継ぐ際、
最終的な仕様は定義書から確認できるが、
その判断に至った背景が共有されないケースが発生した。

Claudeによるレビューでも同様に、
過去の議論を共有していないため、
人間が判断経緯を改めて説明する必要があった。

各AI・各作業環境が参照できるContextは異なるため、
定義書だけではAI間の引き継ぎに不足する場合がある。

## Decision
重要な意思決定について、
判断結果だけでなく判断背景をDecision LogとしてGitHubに残す。

...

## Revisit conditions
AI間でプロジェクトContextを十分かつ安定して共有できるようになった場合、
Decision Logの運用方法および必要性を再評価する。