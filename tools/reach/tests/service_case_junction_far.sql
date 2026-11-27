-- tools/reach/tests/service_case_junction_far.sql：書いたつなぎ目が見つからないと失敗にする（PR #174 のレビュー）
-- J線と L線のつなぎ目を、遠い駅（T1。P から 500 m より遠い）の近くとして書く
INSERT INTO svc_def VALUES ('s-junction', 'テスト鉄道', '連絡線', '普通', 40, false);
INSERT INTO svc_def_line VALUES ('s-junction', 'テスト鉄道', 'J線'), ('s-junction', 'テスト鉄道', 'L線');
INSERT INTO svc_def_point VALUES ('s-junction', 1, 'T5', true, NULL), ('s-junction', 2, 'T7', true, NULL);
INSERT INTO svc_junction VALUES (1, 'テスト鉄道', 'J線', 'テスト鉄道', 'L線', 'T1');
