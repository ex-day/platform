-- tools/reach/tests/service_case_ok.sql：通るはずの系統の定義（service_graph.sql のあとに流す）
-- - s-through：A線 → B線 → M線（テスト地下鉄）の直通。T1・T2・T3・T4・T6 に止まる
-- - s-express：A線・B線の快速。T1・T3・T4 に止まる（T2 は通過）。駅では、その駅の路線の名前で見せる（name_by_line）
-- - s-via：A線と C線を通れるが、T5 を通過地点にして C線を通る（T1・T3 に止まる）
-- - s-loop：A線・B線・D線の環状。T1・T2・T3・T4 に止まり、T4 → T1 は D線
-- - s-junction：J線 → L線。駅でない地点 P で路線が変わる（T5・T7 に止まる）。P は、つなぎ目（T7 の近く）として書く
-- - s-gap：N線の T6・T8 に止まる。Q1–Q2 の線路の切れ目を、つなぎ目（同じ路線、20m まで）として書く
-- - A線を置き換えてよい（T1・T2・T3 は s-through が止まる）
INSERT INTO svc_def VALUES ('s-through', 'テスト鉄道', '直通線', '普通', 40, false, false), ('s-express', 'テスト鉄道', '快速線', '快速', 60, false, true),
                           ('s-via', 'テスト鉄道', '回り道線', '普通', 40, false, false), ('s-loop', 'テスト鉄道', '環状線', '普通', 40, true, false),
                           ('s-junction', 'テスト鉄道', '連絡線', '普通', 40, false, false),
                           ('s-gap', 'テスト鉄道', '切れ目線', '普通', 40, false, false);
INSERT INTO svc_def_line VALUES ('s-through', 'テスト鉄道', 'A線'), ('s-through', 'テスト鉄道', 'B線'), ('s-through', 'テスト地下鉄', 'M線'),
                                ('s-express', 'テスト鉄道', 'A線'), ('s-express', 'テスト鉄道', 'B線'),
                                ('s-via', 'テスト鉄道', 'A線'), ('s-via', 'テスト鉄道', 'C線'),
                                ('s-loop', 'テスト鉄道', 'A線'), ('s-loop', 'テスト鉄道', 'B線'), ('s-loop', 'テスト鉄道', 'D線'),
                                ('s-junction', 'テスト鉄道', 'J線'), ('s-junction', 'テスト鉄道', 'L線'),
                                ('s-gap', 'テスト鉄道', 'N線');
INSERT INTO svc_def_point VALUES
  ('s-through', 1, 'T1', true, NULL), ('s-through', 2, 'T2', true, NULL), ('s-through', 3, 'T3', true, NULL),
  ('s-through', 4, 'T4', true, NULL), ('s-through', 5, 'T6', true, '地下鉄直通線'),
  ('s-express', 1, 'T1', true, NULL), ('s-express', 3, 'T4', true, NULL),
  ('s-via', 1, 'T1', true, NULL), ('s-via', 2, 'T5', false, NULL), ('s-via', 3, 'T3', true, NULL),
  ('s-loop', 1, 'T1', true, NULL), ('s-loop', 2, 'T2', true, NULL), ('s-loop', 3, 'T3', true, NULL), ('s-loop', 4, 'T4', true, NULL),
  ('s-junction', 1, 'T5', true, NULL), ('s-junction', 2, 'T7', true, NULL),
  ('s-gap', 1, 'T6', true, NULL), ('s-gap', 2, 'T8', true, NULL);
-- s-express の T3 は B線の駅で探す（停車駅に路線を書く。駅で見せる名前も B線になる）
INSERT INTO svc_def_point VALUES ('s-express', 2, 'T3', true, NULL, 'テスト鉄道', 'B線');
INSERT INTO svc_replace VALUES ('テスト鉄道', 'A線');
INSERT INTO svc_junction VALUES (1, 'テスト鉄道', 'J線', 'テスト鉄道', 'L線', 'T7', 1),
                                (2, 'テスト鉄道', 'N線', 'テスト鉄道', 'N線', 'T8', 20);
