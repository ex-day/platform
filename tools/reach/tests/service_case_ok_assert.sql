-- tools/reach/tests/service_case_ok_assert.sql：service_case_ok.sql と build_service.sql のあとに流す確かめ
SELECT pg_temp.t_expect('系統6本と停車駅（通過地点は停車駅にしない）',
    (SELECT count(*) FROM reach.build_service WHERE build_id = current_setting('reach.build_id')::bigint) = 6
    AND (SELECT count(*) FROM reach.build_service_stop st JOIN reach.build_service bs ON bs.id = st.build_service_id
          WHERE bs.service_key = 's-via') = 2);
SELECT pg_temp.t_expect('事業者をまたぐ直通：T1 → T6 は乗換なし（4km ÷ 40km/h ＝ 6分）',
    (SELECT abs(minutes - 6) < 1e-6 AND transfers = 0 FROM pg_temp.t_route('T1', 'T6')));
SELECT pg_temp.t_expect('違う系統の間は同じ駅で10分の乗換（快速と直通は T1 で乗換の辺がある）',
    EXISTS (SELECT 1 FROM reach.edge e JOIN reach.node a ON a.id = e.source JOIN reach.node b ON b.id = e.target
              JOIN reach.build_service sa ON sa.id = a.service_id JOIN reach.build_service sb ON sb.id = b.service_id
             WHERE e.kind = 'TRANSFER' AND e.cost = 10
               AND ((sa.service_key = 's-express' AND sb.service_key = 's-through')
                 OR (sa.service_key = 's-through' AND sb.service_key = 's-express'))));
SELECT pg_temp.t_expect('通過地点：s-via の T1 → T3 は C線を通る 3km（A線の 2km ではない）。T5 に s-via の点はない',
    (SELECT abs(e.length_m - 3000) < 1e-6 FROM reach.edge e JOIN reach.build_service bs ON bs.id = e.service_id
      WHERE bs.service_key = 's-via')
    AND NOT EXISTS (SELECT 1 FROM reach.node n JOIN reach.build_service bs ON bs.id = n.service_id
                      JOIN reach.station_node sn ON sn.node_id = n.id JOIN reach.station s ON s.id = sn.station_id
                     WHERE bs.service_key = 's-via' AND s.name = 'T5'));
SELECT pg_temp.t_expect('環状：s-loop は乗車の辺4本（T4 → T1 は D線の 3km）',
    (SELECT count(*) FROM reach.edge e JOIN reach.build_service bs ON bs.id = e.service_id WHERE bs.service_key = 's-loop') = 4
    AND EXISTS (SELECT 1 FROM reach.edge e JOIN reach.build_service bs ON bs.id = e.service_id
                 WHERE bs.service_key = 's-loop' AND abs(e.length_m - 3000) < 1e-6));
SELECT pg_temp.t_expect('置き換え：A線の乗車の辺は外れ、C線は残る。T2 は直通・環状で行ける',
    NOT EXISTS (SELECT 1 FROM reach.edge e JOIN reach.line l ON l.id = e.line_id
                 WHERE e.build_id = current_setting('reach.build_id')::bigint AND l.name = 'A線')
    AND (SELECT count(*) FROM reach.edge e JOIN reach.line l ON l.id = e.line_id
          WHERE e.build_id = current_setting('reach.build_id')::bigint AND l.name = 'C線') = 2
    AND (SELECT transfers = 0 FROM pg_temp.t_route('T1', 'T2')));
SELECT pg_temp.t_expect('辺を2本通る隣の停車駅：快速の T1 → T3 は A線の2本で 2km・2分（60km/h。辺の本数だけ重ねない）',
    (SELECT abs(e.length_m - 2000) < 1e-6 AND abs(e.cost - 2) < 1e-6
       FROM reach.edge e JOIN reach.build_service bs ON bs.id = e.service_id
       JOIN reach.node a ON a.id = e.source JOIN reach.station_node sa ON sa.node_id = a.id JOIN reach.station s1 ON s1.id = sa.station_id
       JOIN reach.node b ON b.id = e.target JOIN reach.station_node sb ON sb.node_id = b.id JOIN reach.station s2 ON s2.id = sb.station_id
      WHERE bs.service_key = 's-express' AND s1.name = 'T1' AND s2.name = 'T3'));
SELECT pg_temp.t_expect('通過地点のある停車駅の間：s-via の T1 → T5 → T3 は 3km・4.5分（区間ごとに1回だけ足す）',
    (SELECT abs(e.cost - 4.5) < 1e-6 FROM reach.edge e JOIN reach.build_service bs ON bs.id = e.service_id
      WHERE bs.service_key = 's-via'));
SELECT pg_temp.t_expect('駅でない地点で路線が変わる：s-junction の T5 → T7 は J線と L線をつないで 2km',
    (SELECT abs(e.length_m - 2000) < 1e-6 FROM reach.edge e JOIN reach.build_service bs ON bs.id = e.service_id
      WHERE bs.service_key = 's-junction'));
SELECT pg_temp.t_expect('駅ごとの路線の名前で見せる系統：s-express は T1 で「A線」、T3（B線の駅と書いた）・T4 で「B線」（停車駅の路線の名前）',
    (SELECT string_agg(s.name || '=' || st.display_name, ',' ORDER BY st.seq)
       FROM reach.build_service_stop st JOIN reach.build_service bs ON bs.id = st.build_service_id
       JOIN reach.station s ON s.id = st.station_id
      WHERE bs.service_key = 's-express') = 'T1=A線,T3=B線,T4=B線');
SELECT pg_temp.t_expect('同じ路線の線路の切れ目：s-gap の T6 → T8 は Q1–Q2 をつないで 1km',
    (SELECT abs(e.length_m - 1000) < 1e-6 FROM reach.edge e JOIN reach.build_service bs ON bs.id = e.service_id
      WHERE bs.service_key = 's-gap'));
ROLLBACK;
