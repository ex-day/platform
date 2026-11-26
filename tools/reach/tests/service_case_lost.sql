-- tools/reach/tests/service_case_lost.sql：置き換えで、外した区間への接続を失うので失敗するはずの定義
-- 快速（T1・T3 に止まり、T2 は通過）だけで A線を置き換える。T2 は F線が残るので「乗れる駅」ではあるが、
-- A線の区間（T1・T2・T3）を行き来できなくなる（PR #173 のレビュー：別の路線だけが残る場合も見つける）
INSERT INTO svc_def VALUES ('s-express', 'テスト鉄道', '快速線', '快速', 60, false);
INSERT INTO svc_def_line VALUES ('s-express', 'テスト鉄道', 'A線');
INSERT INTO svc_def_point VALUES ('s-express', 1, 'T1', true, NULL), ('s-express', 2, 'T3', true, NULL);
INSERT INTO svc_replace VALUES ('テスト鉄道', 'A線');
