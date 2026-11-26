-- tools/reach/tests/service_case_ok.sql：通るはずの系統の定義（service_graph.sql のあとに流す）
-- - s-through：A線 → B線 → M線（テスト地下鉄）の直通。T1・T2・T3・T4・T6 に止まる
-- - s-express：A線・B線の快速。T1・T3・T4 に止まる（T2 は通過）
-- - s-via：A線と C線を通れるが、T5 を通過地点にして C線を通る（T1・T3 に止まる）
-- - s-loop：A線・B線・D線の環状。T1・T2・T3・T4 に止まり、T4 → T1 は D線
-- - A線を置き換えてよい（T1・T2・T3 は s-through が止まる）
INSERT INTO svc_def VALUES ('s-through', 'テスト鉄道', '直通線', '普通', 40, false), ('s-express', 'テスト鉄道', '快速線', '快速', 60, false),
                           ('s-via', 'テスト鉄道', '回り道線', '普通', 40, false), ('s-loop', 'テスト鉄道', '環状線', '普通', 40, true);
INSERT INTO svc_def_line VALUES ('s-through', 'テスト鉄道', 'A線'), ('s-through', 'テスト鉄道', 'B線'), ('s-through', 'テスト地下鉄', 'M線'),
                                ('s-express', 'テスト鉄道', 'A線'), ('s-express', 'テスト鉄道', 'B線'),
                                ('s-via', 'テスト鉄道', 'A線'), ('s-via', 'テスト鉄道', 'C線'),
                                ('s-loop', 'テスト鉄道', 'A線'), ('s-loop', 'テスト鉄道', 'B線'), ('s-loop', 'テスト鉄道', 'D線');
INSERT INTO svc_def_point VALUES
  ('s-through', 1, 'T1', true, NULL), ('s-through', 2, 'T2', true, NULL), ('s-through', 3, 'T3', true, NULL),
  ('s-through', 4, 'T4', true, NULL), ('s-through', 5, 'T6', true, '地下鉄直通線'),
  ('s-express', 1, 'T1', true, NULL), ('s-express', 2, 'T3', true, NULL), ('s-express', 3, 'T4', true, NULL),
  ('s-via', 1, 'T1', true, NULL), ('s-via', 2, 'T5', false, NULL), ('s-via', 3, 'T3', true, NULL),
  ('s-loop', 1, 'T1', true, NULL), ('s-loop', 2, 'T2', true, NULL), ('s-loop', 3, 'T3', true, NULL), ('s-loop', 4, 'T4', true, NULL);
INSERT INTO svc_replace VALUES ('テスト鉄道', 'A線');
