-- ex-day：DB V2 の制約・ビューの確認（DB 設計 v0.2 7節・7.1節、PR #150 のレビュー）
--
-- サンプルデータ（db/sample/R__sample_data.sql）を入れた DB で流す。全体を1つのトランザクションで行い、
-- 最後に ROLLBACK するので、データは変わらない。失敗したところで止まる（ERROR: NG: ...）。
--
--   docker compose exec -T db psql -U exday exday -v ON_ERROR_STOP=1 < db/check/v2_checks.sql
--
-- 同時の更新（企画そのものの会話を2人が同時に作る、公開の Post の数を同時に足す）は、
-- 2つの接続が要るので、このスクリプトではなく db/check/README.md の手順で確かめる。

\set ON_ERROR_STOP 1
BEGIN;

-- 期待どおりのエラー（SQLSTATE）で拒まれることを確かめる
CREATE FUNCTION pg_temp.expect_error(label text, stmt text, expected text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    BEGIN
        EXECUTE stmt;
    EXCEPTION WHEN OTHERS THEN
        IF SQLSTATE = expected THEN
            RAISE NOTICE 'OK: % (%)', label, SQLSTATE;
            RETURN;
        END IF;
        RAISE EXCEPTION 'NG: % は % で拒まれるはずが % (%)', label, expected, SQLSTATE, SQLERRM;
    END;
    RAISE EXCEPTION 'NG: % が拒まれなかった', label;
END $$;

CREATE FUNCTION pg_temp.expect(label text, ok boolean) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF ok IS NOT TRUE THEN RAISE EXCEPTION 'NG: %', label; END IF;
    RAISE NOTICE 'OK: %', label;
END $$;

CREATE FUNCTION pg_temp.sid(k text) RETURNS text LANGUAGE sql IMMUTABLE
    AS $$ SELECT substr(md5('ex-day-sample:' || k), 1, 12) $$;

-- 23505 unique_violation、23503 foreign_key_violation、23514 check_violation

-- ---------------------------------------------------------
-- 1. 場所の採用・取り消し・採用し直し（設計書 3.8節）
-- ---------------------------------------------------------

-- 問い（水車のそば屋）に、既存の場所の候補を2つ足しておく
INSERT INTO place_candidate (public_id, open_question_id, place_id, origin)
SELECT 'chkCandQ0001', q.id, p.id, 'HUMAN' FROM open_question q, place p
 WHERE q.public_id = pg_temp.sid('q:soba') AND p.public_id = pg_temp.sid('place:yokohama-st');
INSERT INTO place_candidate (public_id, open_question_id, place_id, origin)
SELECT 'chkCandQ0002', q.id, p.id, 'HUMAN' FROM open_question q, place p
 WHERE q.public_id = pg_temp.sid('q:soba') AND p.public_id = pg_temp.sid('place:nishi');
INSERT INTO decision (public_id, kind, workflow, method, actor_kind, actor_user_id, actor_role)
SELECT 'chkDecision1', 'PLACE_ADOPT', 'USER_ACTION', 'HUMAN', 'USER', user_id, 'QUESTION_OWNER'
  FROM user_profile WHERE nickname = 'そば好き';

-- 問いを立てた人が1つ目を採用する
INSERT INTO place_adoption (public_id, candidate_id, open_question_id, place_id, actor_kind, actor_user_id, owner_agreement, created_decision_id)
SELECT 'chkAdoptQ001', c.id, c.open_question_id, c.place_id, 'QUESTION_OWNER', d.actor_user_id, 'AGREED', d.id
  FROM place_candidate c, decision d WHERE c.public_id = 'chkCandQ0001' AND d.public_id = 'chkDecision1';

SELECT pg_temp.expect_error('同じ問いに、採用中を2件作る', $q$
    INSERT INTO place_adoption (candidate_id, open_question_id, place_id, actor_kind, actor_user_id, created_decision_id)
    SELECT c.id, c.open_question_id, c.place_id, 'QUESTION_OWNER', d.actor_user_id, d.id
      FROM place_candidate c, decision d WHERE c.public_id = 'chkCandQ0002' AND d.public_id = 'chkDecision1'
$q$, '23505');

SELECT pg_temp.expect_error('取り消しの理由・時点・判断の記録なしで REVOKED にする', $q$
    UPDATE place_adoption SET status = 'REVOKED' WHERE public_id = 'chkAdoptQ001'
$q$, '23514');

-- 採用し直し：同じトランザクションで、前の採用を取り消してから新しい採用を足す
INSERT INTO decision (public_id, kind, workflow, method, actor_kind, actor_user_id, actor_role)
SELECT 'chkDecision2', 'PLACE_READOPT', 'USER_ACTION', 'HUMAN', 'USER', user_id, 'QUESTION_OWNER'
  FROM user_profile WHERE nickname = 'そば好き';
UPDATE place_adoption
   SET status = 'REVOKED', revoke_reason = 'READOPTED', revoked_at = now(),
       revoked_decision_id = (SELECT id FROM decision WHERE public_id = 'chkDecision2')
 WHERE public_id = 'chkAdoptQ001';
INSERT INTO place_adoption (public_id, candidate_id, open_question_id, place_id, actor_kind, actor_user_id, created_decision_id)
SELECT 'chkAdoptQ002', c.id, c.open_question_id, c.place_id, 'QUESTION_OWNER', d.actor_user_id, d.id
  FROM place_candidate c, decision d WHERE c.public_id = 'chkCandQ0002' AND d.public_id = 'chkDecision2';
SELECT pg_temp.expect('採用し直しのあと、問いの採用中・再検討中は1件',
    (SELECT count(*) FROM place_adoption a JOIN open_question q ON q.id = a.open_question_id
      WHERE q.public_id = pg_temp.sid('q:soba') AND a.status IN ('ADOPTED', 'RECONSIDERING')) = 1);
SELECT pg_temp.expect('取り消した採用の行も残る',
    (SELECT status FROM place_adoption WHERE public_id = 'chkAdoptQ001') = 'REVOKED');
SELECT pg_temp.expect('問いへの採用で、その会話が場所と紐付く（conversation_place）',
    EXISTS (SELECT 1 FROM conversation_place cp JOIN conversation c ON c.id = cp.conversation_id
             WHERE c.public_id = pg_temp.sid('conv:soba')));

-- 再検討中にしても、採用中・再検討中は1件のまま
UPDATE place_adoption SET status = 'RECONSIDERING' WHERE public_id = 'chkAdoptQ002';
SELECT pg_temp.expect_error('再検討中のある問いに、採用中を足す', $q$
    INSERT INTO place_adoption (candidate_id, open_question_id, place_id, actor_kind, actor_user_id, created_decision_id)
    SELECT c.id, c.open_question_id, c.place_id, 'QUESTION_OWNER', d.actor_user_id, d.id
      FROM place_candidate c, decision d WHERE c.public_id = 'chkCandQ0001' AND d.public_id = 'chkDecision2'
$q$, '23505');

-- 取り消し：採用をやめる（以降の確認で、一意の制約より先に外部キーが働くよう、問いの採用中をなくしておく）
UPDATE place_adoption
   SET status = 'REVOKED', revoke_reason = 'WITHDRAWN', revoked_at = now(),
       revoked_decision_id = (SELECT id FROM decision WHERE public_id = 'chkDecision2')
 WHERE public_id = 'chkAdoptQ002';
SELECT pg_temp.expect('取り消すと、会話の紐付きもなくなる（場所未定に戻る）',
    NOT EXISTS (SELECT 1 FROM conversation_place cp JOIN conversation c ON c.id = cp.conversation_id
                 WHERE c.public_id = pg_temp.sid('conv:soba')));

-- ---------------------------------------------------------
-- 2. 複合の外部キーと NULL（対象を候補と食い違わせない）
-- ---------------------------------------------------------

-- 採用のない、会話を対象にした候補を足しておく
INSERT INTO place_candidate (public_id, conversation_id, place_id, origin)
SELECT 'chkCandC0001', c.id, p.id, 'HUMAN' FROM conversation c, place p
 WHERE c.public_id = pg_temp.sid('conv:soba') AND p.public_id = pg_temp.sid('place:hikawamaru');

SELECT pg_temp.expect_error('会話の候補を、問いの採用として作る', $q$
    INSERT INTO place_adoption (candidate_id, open_question_id, place_id, actor_kind, created_decision_id)
    SELECT c.id, (SELECT id FROM open_question WHERE public_id = pg_temp.sid('q:soba')), c.place_id, 'RULE_OR_OPERATOR',
           (SELECT id FROM decision WHERE public_id = 'chkDecision1')
      FROM place_candidate c WHERE c.public_id = 'chkCandC0001'
$q$, '23503');

SELECT pg_temp.expect_error('問いの候補を、会話の採用として作る', $q$
    INSERT INTO place_adoption (candidate_id, conversation_id, place_id, actor_kind, created_decision_id)
    SELECT c.id, (SELECT id FROM conversation WHERE public_id = pg_temp.sid('conv:soba')), c.place_id, 'RULE_OR_OPERATOR',
           (SELECT id FROM decision WHERE public_id = 'chkDecision1')
      FROM place_candidate c WHERE c.public_id = 'chkCandQ0001'
$q$, '23503');

SELECT pg_temp.expect_error('別の会話の候補を、この会話の採用として作る', $q$
    INSERT INTO place_adoption (candidate_id, conversation_id, place_id, actor_kind, created_decision_id)
    SELECT c.id, (SELECT id FROM conversation WHERE public_id = pg_temp.sid('conv:kozukue')), c.place_id, 'RULE_OR_OPERATOR',
           (SELECT id FROM decision WHERE public_id = 'chkDecision1')
      FROM place_candidate c WHERE c.public_id = 'chkCandC0001'
$q$, '23503');

SELECT pg_temp.expect_error('対象を2つ持つ採用', $q$
    INSERT INTO place_adoption (candidate_id, conversation_id, open_question_id, place_id, actor_kind, created_decision_id)
    SELECT c.id, c.conversation_id, (SELECT id FROM open_question WHERE public_id = pg_temp.sid('q:soba')), c.place_id,
           'RULE_OR_OPERATOR', (SELECT id FROM decision WHERE public_id = 'chkDecision1')
      FROM place_candidate c WHERE c.public_id = 'chkCandC0001'
$q$, '23514');

SELECT pg_temp.expect_error('同じ候補に、採用中を2件作る（会話）', $q$
    INSERT INTO place_adoption (candidate_id, conversation_id, place_id, actor_kind, created_decision_id)
    SELECT c.id, c.conversation_id, c.place_id, 'RULE_OR_OPERATOR', (SELECT id FROM decision WHERE public_id = 'chkDecision1')
      FROM place_candidate c WHERE c.public_id = pg_temp.sid('cand:walk-yamashita')
$q$, '23505');

SELECT pg_temp.expect_error('既存の場所と新しい場所の案を両方持つ候補', $q$
    INSERT INTO place_candidate (conversation_id, place_id, proposed_name, origin)
    SELECT id, (SELECT id FROM place LIMIT 1), '案', 'HUMAN' FROM conversation WHERE public_id = pg_temp.sid('conv:soba')
$q$, '23514');

SELECT pg_temp.expect_error('同じ対象に、同じ既存の場所の候補を2つ作る', $q$
    INSERT INTO place_candidate (conversation_id, place_id, origin)
    SELECT conversation_id, place_id, 'HUMAN' FROM place_candidate WHERE public_id = pg_temp.sid('cand:walk-yamashita')
$q$, '23505');

-- ---------------------------------------------------------
-- 3. 企画そのものの会話は企画ごとに1つ（同時の作成は README の手順）
-- ---------------------------------------------------------

SELECT pg_temp.expect_error('同じ企画に、企画そのものの会話を2つ作る', $q$
    INSERT INTO conversation (entry_project_id, is_project_own, starter_user_id)
    SELECT id, true, creator_user_id FROM project WHERE public_id = pg_temp.sid('proj:slopes')
$q$, '23505');
SELECT pg_temp.expect('企画をきっかけにした会話（企画そのものではない）は、同じ企画にいくつでも作れる',
    (SELECT count(*) FROM conversation c JOIN project p ON p.id = c.entry_project_id
      WHERE p.public_id = pg_temp.sid('proj:slopes') AND NOT c.is_project_own) >= 1);

-- ---------------------------------------------------------
-- 4. 公開のビューの境界（設計書 2.5節）
-- ---------------------------------------------------------

SELECT pg_temp.expect('非公開の Post は visible_post に出ない',
    NOT EXISTS (SELECT 1 FROM visible_post WHERE public_id = pg_temp.sid('post:walk-4')));
SELECT pg_temp.expect('公開の Post の数の写しは、非公開の Post を数えない',
    (SELECT public_post_count FROM conversation WHERE public_id = pg_temp.sid('conv:walk'))
     = (SELECT count(*) FROM visible_post vp JOIN conversation c ON c.id = vp.conversation_id
         WHERE c.public_id = pg_temp.sid('conv:walk')));
SELECT pg_temp.expect('成立を取り消した Discovery は visible_discovery に出ない',
    NOT EXISTS (SELECT 1 FROM visible_discovery WHERE public_id = pg_temp.sid('disc:bamboo'))
    AND EXISTS (SELECT 1 FROM discovery WHERE public_id = pg_temp.sid('disc:bamboo')));

-- 会話を非公開にすると、その Post・わかってきたこと・問い・場所の候補も一般には出ない
UPDATE conversation SET visibility = 'HIDDEN' WHERE public_id = pg_temp.sid('conv:walk');
SELECT pg_temp.expect('非公開の会話の Post は出ない',
    NOT EXISTS (SELECT 1 FROM visible_post vp JOIN conversation c ON c.id = vp.conversation_id WHERE c.public_id = pg_temp.sid('conv:walk')));
SELECT pg_temp.expect('非公開の会話のわかってきたことは出ない',
    NOT EXISTS (SELECT 1 FROM visible_finding WHERE public_id = pg_temp.sid('finding:walk-value')));
SELECT pg_temp.expect('非公開の会話の場所の候補は出ない',
    NOT EXISTS (SELECT 1 FROM visible_place_candidate WHERE public_id = pg_temp.sid('cand:walk-yamashita')));
UPDATE conversation SET visibility = 'HIDDEN' WHERE public_id = pg_temp.sid('conv:soba');
SELECT pg_temp.expect('非公開の会話の問い・問いを対象にした場所の候補は出ない',
    NOT EXISTS (SELECT 1 FROM visible_open_question WHERE public_id = pg_temp.sid('q:soba'))
    AND NOT EXISTS (SELECT 1 FROM visible_place_candidate WHERE public_id = pg_temp.sid('cand:soba-west')));

-- 入口の企画が非公開になっても、企画をきっかけにした会話は非公開にならない
UPDATE project SET visibility = 'HIDDEN' WHERE public_id = pg_temp.sid('proj:slopes');
SELECT pg_temp.expect('入口の企画が非公開でも、そこから始まった会話は出る',
    EXISTS (SELECT 1 FROM visible_conversation WHERE public_id = pg_temp.sid('conv:slopes-snow')));
SELECT pg_temp.expect('非公開の企画の項目・変更の出来事は出ない',
    NOT EXISTS (SELECT 1 FROM visible_project_item WHERE public_id = pg_temp.sid('item:slopes-a'))
    AND NOT EXISTS (SELECT 1 FROM visible_project_change WHERE public_id = pg_temp.sid('change:slopes-created')));

-- 公開の企画の項目が、見えない Discovery を指すときは出ない
UPDATE discovery SET visibility = 'HIDDEN' WHERE public_id = pg_temp.sid('disc:walk');
SELECT pg_temp.expect('非公開の Discovery を指す企画の項目は出ない（場所を指す項目は出る）',
    NOT EXISTS (SELECT 1 FROM visible_project_item WHERE public_id = pg_temp.sid('item:course-1'))
    AND EXISTS (SELECT 1 FROM visible_project_item WHERE public_id = pg_temp.sid('item:course-2')));

-- ---------------------------------------------------------
-- 5. 企画の変更の履歴（設計書 3.5節）
-- ---------------------------------------------------------

-- 変更の出来事を重ねると、今の並びになる（項目ごとに最後の出来事が今の順番）
SELECT pg_temp.expect('鶴見の急坂番付：変更の出来事から今の並びを再現できる', NOT EXISTS (
    WITH last_event AS (
        SELECT DISTINCT ON (ci.place_id, ci.discovery_id) ci.place_id, ci.discovery_id, ci.kind, ci.after_position
          FROM project_change_item ci JOIN project_change ch ON ch.id = ci.change_id
         WHERE ch.project_id = (SELECT id FROM project WHERE public_id = pg_temp.sid('proj:slopes'))
         ORDER BY ci.place_id, ci.discovery_id, ch.changed_at DESC, ch.id DESC
    ),
    replayed AS (SELECT place_id, discovery_id, after_position AS position FROM last_event WHERE kind <> 'REMOVED'),
    current AS (SELECT place_id, discovery_id, position FROM project_item
                 WHERE project_id = (SELECT id FROM project WHERE public_id = pg_temp.sid('proj:slopes')))
    (SELECT * FROM replayed EXCEPT SELECT * FROM current)
    UNION ALL
    (SELECT * FROM current EXCEPT SELECT * FROM replayed)
));

SELECT pg_temp.expect_error('同じ企画に「作った」の出来事を2つ作る', $q$
    INSERT INTO project_change (project_id, kind, changed_by_user_id)
    SELECT id, 'CREATED', creator_user_id FROM project WHERE public_id = pg_temp.sid('proj:slopes')
$q$, '23505');
SELECT pg_temp.expect_error('順番の変わらない MOVED', $q$
    INSERT INTO project_change_item (change_id, place_id, kind, before_position, after_position)
    SELECT id, (SELECT id FROM place WHERE public_id = pg_temp.sid('place:slope-a')), 'MOVED', 1, 1
      FROM project_change WHERE public_id = pg_temp.sid('change:slopes-add-d')
$q$, '23514');
SELECT pg_temp.expect_error('中身を写す項目名（表題・呼びかけ・説明以外）', $q$
    INSERT INTO project_change (project_id, kind, changed_by_user_id, fields_changed)
    SELECT id, 'UPDATED', creator_user_id, ARRAY['NOTE'] FROM project WHERE public_id = pg_temp.sid('proj:slopes')
$q$, '23514');

-- 並べ替え：順番の一意は遅延の制約なので、1つの更新で入れ替えられる
UPDATE project_item SET position = CASE position WHEN 1 THEN 2 WHEN 2 THEN 1 END
 WHERE project_id = (SELECT id FROM project WHERE public_id = pg_temp.sid('proj:slopes')) AND position IN (1, 2);
SET CONSTRAINTS project_item_position_uniq IMMEDIATE;
SELECT pg_temp.expect('項目の順番を入れ替えられる', true);
SET CONSTRAINTS project_item_position_uniq DEFERRED;

-- ---------------------------------------------------------
-- 6. 統合の自己参照（設計書 3.12節）。循環は統合の処理で確かめる
-- ---------------------------------------------------------

SELECT pg_temp.expect_error('場所の統合先を自分自身にする', $q$
    UPDATE place SET status = 'MERGED', merged_into_place_id = id WHERE public_id = pg_temp.sid('place:slope-a')
$q$, '23514');
SELECT pg_temp.expect_error('Discovery を自分自身に統合する', $q$
    INSERT INTO discovery_relation (from_discovery_id, to_discovery_id, kind, created_decision_id)
    SELECT id, id, 'MERGED', (SELECT id FROM decision WHERE public_id = 'chkDecision1') FROM discovery WHERE public_id = pg_temp.sid('disc:roses')
$q$, '23514');
SELECT pg_temp.expect_error('統合先なしで MERGED にする', $q$
    UPDATE place SET status = 'MERGED' WHERE public_id = pg_temp.sid('place:slope-a')
$q$, '23514');

-- ---------------------------------------------------------
-- 7. そのほかの制約
-- ---------------------------------------------------------

SELECT pg_temp.expect_error('Reaction の対象と種類が合わない（Post に「行ってみたい」）', $q$
    INSERT INTO reaction (user_id, post_id, kind)
    SELECT (SELECT user_id FROM user_profile WHERE nickname = 'そば好き'), id, 'WANT_TO_GO' FROM post WHERE public_id = pg_temp.sid('post:walk-1')
$q$, '23514');
SELECT pg_temp.expect_error('同じ人が同じ Post に同じ Reaction を2回', $q$
    INSERT INTO reaction (user_id, post_id, kind)
    SELECT (SELECT user_id FROM user_profile WHERE nickname = 'はまっこ'), id, 'EMPATHY' FROM post WHERE public_id = pg_temp.sid('post:walk-1')
$q$, '23505');
SELECT pg_temp.expect_error('1つの投稿に場所の共有の誘いを2件', $q$
    INSERT INTO location_sharing_prompt (post_id) SELECT id FROM post WHERE public_id = pg_temp.sid('post:walk-1')
$q$, '23505');
SELECT pg_temp.expect_error('システムの判断の記録に User を持たせる', $q$
    INSERT INTO decision (kind, workflow, method, actor_kind, actor_user_id) VALUES ('ESTABLISH', 'ANALYSIS', 'AUTO', 'SYSTEM', 1)
$q$, '23514');
SELECT pg_temp.expect_error('由来の種類と対象が合わない（会話の由来に Post を指す）', $q$
    INSERT INTO discovery_origin (discovery_id, kind, post_id, decision_id)
    SELECT d.id, 'CONVERSATION', (SELECT id FROM post LIMIT 1), (SELECT id FROM decision WHERE public_id = 'chkDecision1')
      FROM discovery d WHERE d.public_id = pg_temp.sid('disc:roses')
$q$, '23514');
SELECT pg_temp.expect('public_id の既定値が英数字12文字', exday_public_id() ~ '^[0-9A-Za-z]{12}$');

ROLLBACK;
\echo 'v2_checks: すべて OK（変更は ROLLBACK した）'
