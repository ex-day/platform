// see docs/ui/screens/S08-user-edit.md, docs/ui/wireframe/screens/S08/*.png
//
// S08(ユーザー情報更新)の本体。「プロフィール」と「アカウント」の2つのセクションに分ける(Issue #76)。
// - プロフィール：ニックネーム・興味のある地域・興味のある〇〇を「更新」でまとめて保存する。
//   ニックネームは更新押下時にチェックし、問題があれば入力欄の近くに理由を示して更新しない(Issue #74)。
// - アカウント：ログイン方法と退会。それぞれ個別の操作とし、「更新」ボタンを使わない。
//   ログイン方法の詳細はIssue #75の判断待ち。モックでは案のとおり最後の1つを解除できないようにする。
// - 未ログインのときはC08(ログイン認証ダイアログ)でログインを求め、ユーザー情報は表示しない。
"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import { CheckCircle2Icon, LockIcon } from "lucide-react";
import { Button } from "@/components/primitives/button";
import { Dialog } from "@/components/primitives/dialog";
import { ChipSuggestField, type SuggestOption } from "@/components/layout/ChipSuggestField";
import { useMockAuth } from "@/lib/mock-auth";
import { suggestAreas } from "@/lib/mock-data/areas";
import { suggestTags } from "@/lib/mock-data/tags";
import type { InterestArea } from "@/lib/mock-data/user";
import {
  NICKNAME_ERROR_MESSAGES,
  NICKNAME_MAX_WIDTH,
  nicknameWidth,
  normalizeNickname,
  validateNickname,
  type NicknameError,
} from "@/lib/nickname";
import { cn } from "@/lib/utils";

type Provider = "Google" | "Apple";
type LoginMethod = { provider: Provider; connected: boolean };

const INITIAL_METHODS: Record<"google" | "both" | "apple", LoginMethod[]> = {
  google: [
    { provider: "Google", connected: true },
    { provider: "Apple", connected: false },
  ],
  both: [
    { provider: "Google", connected: true },
    { provider: "Apple", connected: true },
  ],
  apple: [
    { provider: "Google", connected: false },
    { provider: "Apple", connected: true },
  ],
};

/** [Mock確認用] ニックネームの入力チェックを試すための入力例 */
const NICKNAME_SAMPLES: { label: string; value: string }[] = [
  { label: "ななしさん", value: "ななしさん" },
  { label: "空白だけ", value: "　 　" },
  { label: "改行入り", value: "夕景\nさんぽ" },
  { label: "半角25文字", value: "abcdefghijklmnopqrstuvwxy" },
  { label: "全角13文字", value: "あいうえおかきくけこさしす" },
  { label: "絵文字12個", value: "🌇🌇🌇🌇🌇🌇🌇🌇🌇🌇🌇🌇" },
  { label: "絵文字13個", value: "🌇🌇🌇🌇🌇🌇🌇🌇🌇🌇🌇🌇🌇" },
  { label: "ex-day公式ガイド", value: "ex-day公式ガイド" },
  { label: "EXDAY", value: "EXDAY" },
  { label: "ｅｘ－ｄａｙ", value: "ｅｘ－ｄａｙ" },
  { label: "Ex Day", value: "Ex Day" },
  { label: "運営スタッフ", value: "運営スタッフ" },
];

function SectionHeading({ children }: { children: string }) {
  return <h2 className="text-lg font-bold">{children}</h2>;
}

function PrivateNote({ id, children }: { id?: string; children: string }) {
  return (
    <p id={id} className="flex items-start gap-1 text-xs text-muted-foreground">
      <LockIcon className="mt-0.5 h-3 w-3 shrink-0" aria-hidden />
      {children}
    </p>
  );
}

export function UserEdit({
  auth,
  methods,
  initialWithdrawOpen,
}: {
  auth: "member" | "guest";
  methods: "google" | "both" | "apple";
  initialWithdrawOpen: boolean;
}) {
  const { user, login, logout, openLogin } = useMockAuth();

  // 比較用クエリに合わせてログイン状態をそろえる(切替時にこのコンポーネントを作り直す)
  useEffect(() => {
    if (auth === "guest") {
      logout();
      openLogin();
    } else if (!user) {
      login("Google");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth]);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-bold">ユーザー情報更新</h1>
        <p className="rounded-md bg-muted px-3 py-2 text-sm text-muted-foreground">ニックネーム以外は他の人に表示されません。</p>
      </div>
      {user ? (
        <>
          <ProfileSection />
          <AccountSection methods={methods} initialWithdrawOpen={initialWithdrawOpen} />
        </>
      ) : (
        <section className="flex flex-col items-start gap-3 rounded-lg border p-6">
          <p className="text-sm">ユーザー情報の確認・変更には、ログインが必要です。</p>
          <Button onClick={openLogin}>ログイン</Button>
        </section>
      )}
    </div>
  );
}

function ProfileSection() {
  const { profile, updateProfile } = useMockAuth();
  const nicknameId = useId();
  const nicknameErrorId = useId();
  const nicknameHelpId = useId();
  const areaNoteId = useId();
  const interestNoteId = useId();
  const nicknameRef = useRef<HTMLInputElement>(null);

  const [nickname, setNickname] = useState(profile.nickname);
  const [areas, setAreas] = useState<InterestArea[]>(profile.interestAreas);
  const [interests, setInterests] = useState<string[]>(profile.interests);
  const [areaQuery, setAreaQuery] = useState("");
  const [interestQuery, setInterestQuery] = useState("");
  const [error, setError] = useState<NicknameError | null>(null);
  const [saved, setSaved] = useState(false);

  const width = nicknameWidth(normalizeNickname(nickname));

  const areaOptions: SuggestOption[] = suggestAreas(areaQuery, areas.map((a) => a.id)).map((area) => ({
    id: area.id,
    text: `${area.name}（${area.note}）`,
    label: (
      <>
        {area.name}
        <span className="ml-2 text-xs text-muted-foreground">{area.note}</span>
      </>
    ),
    onSelect: () => {
      setAreas((prev) => [...prev, { id: area.id, name: area.name }]);
      setAreaQuery("");
      setSaved(false);
    },
  }));

  // 案：保管されているタグから補完して選び、該当がなければ自分の言葉で追加する(F04と同じ仕組み。Issue #73の判断待ち)
  const interestWord = interestQuery.trim().replace(/^[#＃]+/, "").trim();
  const addInterest = (word: string) => {
    setInterests((prev) => (prev.includes(word) ? prev : [...prev, word]));
    setInterestQuery("");
    setSaved(false);
  };
  const tagOptions: SuggestOption[] = interestWord
    ? suggestTags(interestWord)
        .filter((tag) => !interests.includes(tag.name))
        .map((tag) => ({
          id: `tag-${tag.name}`,
          text: `#${tag.name}（${tag.count}件）`,
          label: (
            <>
              #{tag.name}
              <span className="ml-2 text-xs text-muted-foreground">{tag.count}件</span>
            </>
          ),
          onSelect: () => addInterest(tag.name),
        }))
    : [];
  const canAddOwnWord =
    interestWord.length > 0 && !interests.includes(interestWord) && !tagOptions.some((o) => o.id === `tag-${interestWord}`);
  const interestOptions: SuggestOption[] = canAddOwnWord
    ? [
        ...tagOptions,
        {
          id: "own-word",
          text: `「${interestWord}」を自分の言葉で追加`,
          label: (
            <>
              「{interestWord}」を自分の言葉で追加
            </>
          ),
          onSelect: () => addInterest(interestWord),
        },
      ]
    : tagOptions;

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    const nextError = validateNickname(nickname);
    setError(nextError);
    if (nextError) {
      setSaved(false);
      nicknameRef.current?.focus();
      return;
    }
    // 前後の空白を削ってから保存する。重複の確認はしない
    const normalized = normalizeNickname(nickname);
    setNickname(normalized);
    updateProfile({ nickname: normalized, interestAreas: areas, interests });
    setSaved(true);
  };

  return (
    <section aria-labelledby={`${nicknameId}-section`} className="rounded-lg border p-5 sm:p-6">
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-6">
        <div id={`${nicknameId}-section`}>
          <SectionHeading>プロフィール</SectionHeading>
        </div>

        {/* [Mock確認用] ニックネームの入力チェックを試す入力例と、表示名の反映先 */}
        <div className="flex flex-col gap-1 rounded-md border border-dashed bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <span className="font-medium">[Mock確認用] ニックネームの入力例:</span>
            {NICKNAME_SAMPLES.map((sample) => (
              <button
                key={sample.label}
                type="button"
                onClick={() => {
                  setNickname(sample.value);
                  setError(null);
                  setSaved(false);
                }}
                className="hover:text-foreground hover:underline"
              >
                {sample.label}
              </button>
            ))}
          </div>
          <p>
            表示名の反映：
            <Link href="/discoveries/sample-1" className="underline hover:text-foreground">
              S03のみんなの声
            </Link>
            （「過去のやりとりをすべて見る」で展開した3件目がこのユーザーの声）
          </p>
        </div>

        <div className="flex flex-col gap-2">
          <div className="flex items-center gap-2">
            <label htmlFor={nicknameId} className="text-sm font-semibold">
              ニックネーム
            </label>
            <span className="rounded bg-destructive px-1.5 py-0.5 text-[11px] font-semibold text-white">必須</span>
          </div>
          <div className="flex items-center gap-3">
            <input
              ref={nicknameRef}
              id={nicknameId}
              type="text"
              value={nickname}
              onChange={(event) => {
                setNickname(event.target.value);
                setSaved(false);
              }}
              aria-invalid={error ? true : undefined}
              aria-describedby={cn(error && nicknameErrorId, nicknameHelpId)}
              className={cn(
                "h-10 w-full rounded-md border bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring sm:max-w-sm",
                error && "border-destructive focus-visible:ring-destructive",
              )}
            />
            <span
              aria-label={`${width}／${NICKNAME_MAX_WIDTH}`}
              className={cn(
                "shrink-0 font-mono text-xs text-muted-foreground",
                width > NICKNAME_MAX_WIDTH && "font-semibold text-destructive",
              )}
            >
              {width} / {NICKNAME_MAX_WIDTH}
            </span>
          </div>
          {error ? (
            <p id={nicknameErrorId} role="alert" className="text-sm font-semibold text-destructive">
              {NICKNAME_ERROR_MESSAGES[error]}
            </p>
          ) : null}
          <div id={nicknameHelpId} className="flex flex-col gap-0.5 text-xs text-muted-foreground">
            <p>全角12文字（半角24文字）相当まで。他の人と同じ名前でもかまいません。</p>
            <p>変更すると、これまでの声（投稿）の表示名にもすぐ反映されます。</p>
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <div className="flex flex-wrap items-baseline gap-2">
            <h3 className="text-sm font-semibold">興味のある地域</h3>
            <span className="text-xs text-muted-foreground">任意・複数</span>
          </div>
          <PrivateNote id={areaNoteId}>他の人には表示されません。おすすめと、地域の問いのお知らせに使います。</PrivateNote>
          <ChipSuggestField
            label="興味のある地域"
            chips={areas.map((a) => ({ id: a.id, label: a.name }))}
            onRemove={(id) => {
              setAreas((prev) => prev.filter((a) => a.id !== id));
              setSaved(false);
            }}
            query={areaQuery}
            onQueryChange={setAreaQuery}
            options={areaOptions}
            placeholder="地名を入力して追加（市区町村を補完）"
            footnote="候補の元データ・粒度は検討事項（モックはダミーの地域）"
            describedBy={areaNoteId}
          />
        </div>

        <div className="flex flex-col gap-2">
          <div className="flex flex-wrap items-baseline gap-2">
            <h3 className="text-sm font-semibold">興味のある〇〇</h3>
            <span className="text-xs text-muted-foreground">任意・複数</span>
            <span className="rounded border px-1.5 text-[11px] text-muted-foreground">項目名・入力方法は #73 判断待ち</span>
          </div>
          <PrivateNote id={interestNoteId}>他の人には表示されません。保管されているタグから選ぶか、自分の言葉で追加できます。</PrivateNote>
          <ChipSuggestField
            label="興味のある〇〇"
            chips={interests.map((word) => ({ id: word, label: `#${word}` }))}
            onRemove={(id) => {
              setInterests((prev) => prev.filter((w) => w !== id));
              setSaved(false);
            }}
            query={interestQuery}
            onQueryChange={setInterestQuery}
            options={interestOptions}
            placeholder="言葉を入力して追加"
            describedBy={interestNoteId}
          />
        </div>

        <div className="flex flex-col gap-3 border-t pt-4 sm:flex-row sm:items-center sm:justify-end">
          {saved ? (
            <p role="status" className="flex items-center gap-1 text-sm text-muted-foreground">
              <CheckCircle2Icon className="h-4 w-4 text-primary" aria-hidden />
              プロフィールを更新しました
            </p>
          ) : null}
          <Button type="submit" className="w-full rounded-full sm:w-auto sm:px-8">
            更新
          </Button>
        </div>
      </form>
    </section>
  );
}

function AccountSection({
  methods,
  initialWithdrawOpen,
}: {
  methods: "google" | "both" | "apple";
  initialWithdrawOpen: boolean;
}) {
  const router = useRouter();
  const { logout } = useMockAuth();
  const headingId = useId();
  const [loginMethods, setLoginMethods] = useState<LoginMethod[]>(INITIAL_METHODS[methods]);
  const [message, setMessage] = useState("");
  const [withdrawOpen, setWithdrawOpen] = useState(initialWithdrawOpen);
  const connectedCount = loginMethods.filter((m) => m.connected).length;

  const toggle = (provider: Provider, connected: boolean) => {
    setLoginMethods((prev) => prev.map((m) => (m.provider === provider ? { ...m, connected } : m)));
    // プロバイダから受け取る名前・顔写真は取り込まない(Issue #75)
    setMessage(connected ? `${provider}とつなぎました（モック）` : `${provider}のつながりを解除しました（モック）`);
  };

  const withdraw = () => {
    // 退会処理(モック)。端末にかかわらずS01へ遷移する
    setWithdrawOpen(false);
    logout();
    router.push("/");
  };

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-6 rounded-lg border p-5 sm:p-6">
      <div id={headingId}>
        <SectionHeading>アカウント</SectionHeading>
      </div>

      <div className="flex flex-col gap-2">
        <div className="flex flex-wrap items-baseline gap-2">
          <h3 className="text-sm font-semibold">ログイン方法</h3>
          <span className="rounded border px-1.5 text-[11px] text-muted-foreground">詳細は #75 判断待ち</span>
        </div>
        <ul className="flex flex-col gap-2">
          {loginMethods.map((method) => {
            const isLast = method.connected && connectedCount === 1;
            return (
              <li key={method.provider} className="flex items-center gap-3 rounded-md border px-4 py-3">
                <span
                  aria-hidden
                  className="hidden h-7 w-7 shrink-0 items-center justify-center rounded-full border text-xs sm:flex"
                >
                  {method.provider[0]}
                </span>
                <div className="flex min-w-0 flex-1 flex-col sm:flex-row sm:items-baseline sm:gap-3">
                  <span className="text-sm font-medium">{method.provider}</span>
                  <span className="text-xs text-muted-foreground">
                    {method.connected ? "つながっています" : "つながっていません"}
                  </span>
                </div>
                {method.connected ? (
                  <Button
                    variant="outline"
                    size="sm"
                    className="rounded-full px-4"
                    disabled={isLast}
                    aria-describedby={isLast ? `${headingId}-last` : undefined}
                    onClick={() => toggle(method.provider, false)}
                  >
                    解除
                  </Button>
                ) : (
                  <Button variant="outline" size="sm" className="rounded-full px-4" onClick={() => toggle(method.provider, true)}>
                    つなぐ
                  </Button>
                )}
              </li>
            );
          })}
        </ul>
        <p id={`${headingId}-last`} className="text-xs text-muted-foreground">
          それぞれ個別に操作します。最後の1つは解除できません。
        </p>
        <p role="status" className="text-xs text-muted-foreground">
          {message}
        </p>
      </div>

      <div className="flex flex-col gap-2 border-t pt-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h3 className="text-sm font-semibold">退会</h3>
          <p className="text-xs text-muted-foreground">退会すると元に戻せません。</p>
        </div>
        <button
          type="button"
          onClick={() => setWithdrawOpen(true)}
          className="w-fit text-sm underline underline-offset-4 hover:text-destructive"
        >
          退会する
        </button>
      </div>

      <Dialog open={withdrawOpen} onClose={() => setWithdrawOpen(false)} label="退会しますか？">
        <div className="flex flex-col gap-4">
          <h2 className="text-lg font-bold">退会しますか？</h2>
          <p className="text-sm">退会すると、アカウントは元に戻せません。ログイン方法のつながりも解除されます。</p>
          <p className="rounded-md border border-dashed px-3 py-2 text-xs text-muted-foreground">
            これまでの声（投稿）の扱い：[判断待ち]（残す場合の表示名、削除する場合の会話・わかってきたことへの影響。S08 検討事項）
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="outline" className="rounded-full px-5" onClick={() => setWithdrawOpen(false)}>
              キャンセル
            </Button>
            <Button className="rounded-full px-5" onClick={withdraw}>
              退会する
            </Button>
          </div>
        </div>
      </Dialog>
    </section>
  );
}
