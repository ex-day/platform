-- tools/reach/tests/service_case_junction_missing.sql：つなぎ目を書かないと、駅でない地点では路線が変わらない（PR #174 のレビュー）
-- s-junction：J線 → L線（T5・T7 に止まる）。P の J線と L線の点は同じ位置にあるが、つなぎ目として書いていない
-- （立体交差でも同じ位置になるので、自動ではつながない）。T5 → T7 は線路でつながらず、版を作るのを失敗にする
INSERT INTO svc_def VALUES ('s-junction', 'テスト鉄道', '連絡線', '普通', 40, false);
INSERT INTO svc_def_line VALUES ('s-junction', 'テスト鉄道', 'J線'), ('s-junction', 'テスト鉄道', 'L線');
INSERT INTO svc_def_point VALUES ('s-junction', 1, 'T5', true, NULL), ('s-junction', 2, 'T7', true, NULL);
