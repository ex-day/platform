-- tools/reach/tests/service_case_parallel.sql：置き換えで、並ぶ別の路線を走る系統を代わりに数えてしまわないことを確かめる定義
-- 快速（A線。T1・T3 に止まり、T2 は通過）で A線を置き換える。各停（lines に A線と G線。T1・T2・T3 に止まる）は、短い G線を
-- 走るので、A線の T2 への接続の代わりにならない → 失敗するはず（PR #173 の再レビュー）
INSERT INTO svc_def VALUES ('s-express', 'テスト鉄道', '快速線', '快速', 60, false), ('s-local', 'テスト鉄道', '各停線', '普通', 40, false);
INSERT INTO svc_def_line VALUES ('s-express', 'テスト鉄道', 'A線'), ('s-local', 'テスト鉄道', 'A線'), ('s-local', 'テスト鉄道', 'G線');
INSERT INTO svc_def_point VALUES ('s-express', 1, 'T1', true, NULL), ('s-express', 2, 'T3', true, NULL),
                                 ('s-local', 1, 'T1', true, NULL), ('s-local', 2, 'T2', true, NULL), ('s-local', 3, 'T3', true, NULL);
INSERT INTO svc_replace VALUES ('テスト鉄道', 'A線');
