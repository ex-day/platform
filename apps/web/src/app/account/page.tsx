// see docs/ui/screens/S08-user-edit.md, docs/ui/wireframe/screens/S08/*.png
//
// S08(ユーザー情報更新)。ログインしているユーザー本人の画面で、URLにユーザーの識別子を含めない。
// ダミーデータ・API未接続。更新した内容はMockAuthProviderの状態としてのみ保持し、再読み込みで失われる。
//
// Wireframeで確認した状態差をsearchParamsで切り替える(Issue #79)。画面上部の[Mock比較用]バーから切り替えられる。
// - auth: "member"(ログイン済み) | "guest"(未ログイン。C08でログインを求める)。既定値は"member"。
//   "member"で開いた時点で未ログインなら、モックのユーザーとしてログインした状態にする。
// - methods: ログイン方法の初期状態。"google"(Googleのみ) | "both"(Google・Apple) | "apple"(Appleのみ)。既定値は"google"。
// - dialog: "withdraw"で退会の確認ダイアログを開いた状態にする。
import Link from "next/link";
import { Header } from "@/components/C01-header/Header";
import { Footer } from "@/components/C02-footer/Footer";
import { UserEdit } from "./UserEdit";

type Params = Record<string, string | string[] | undefined>;

const AUTH_OPTIONS = ["member", "guest"] as const;
const METHOD_OPTIONS = ["google", "both", "apple"] as const;
const DIALOG_OPTIONS = ["none", "withdraw"] as const;

const LABELS: Record<string, string> = {
  member: "ログイン済み",
  guest: "未ログイン",
  google: "Googleのみ",
  both: "Google・Apple",
  apple: "Appleのみ",
  none: "閉じる",
  withdraw: "開く",
};

const first = (v: string | string[] | undefined) => (Array.isArray(v) ? v[0] : v);
function pick<T extends readonly string[]>(v: string | string[] | undefined, options: T, fallback: T[number]): T[number] {
  const x = first(v);
  return (options as readonly string[]).includes(x ?? "") ? (x as T[number]) : fallback;
}

export default async function AccountPage({ searchParams }: { searchParams: Promise<Params> }) {
  const params = await searchParams;
  const current = {
    auth: pick(params.auth, AUTH_OPTIONS, "member"),
    methods: pick(params.methods, METHOD_OPTIONS, "google"),
    dialog: pick(params.dialog, DIALOG_OPTIONS, "none"),
  };
  type Key = keyof typeof current;

  const hrefWith = (key: Key, value: string) => {
    const next = new URLSearchParams({ ...current, [key]: value });
    if (next.get("dialog") === "none") next.delete("dialog");
    return `/account?${next.toString()}`;
  };
  const renderToggle = (label: string, key: Key, options: readonly string[]) => (
    <span>
      {label}:
      {options.map((opt) => (
        <Link
          key={opt}
          href={hrefWith(key, opt)}
          scroll={false}
          replace
          aria-current={opt === current[key]}
          className={opt === current[key] ? "mx-1 font-semibold text-foreground underline" : "mx-1 hover:underline"}
        >
          {LABELS[opt] ?? opt}
        </Link>
      ))}
    </span>
  );

  return (
    <>
      <Header />
      <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-4 px-4 py-6">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-md border border-dashed bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
          <span className="font-medium">[Mock比較用]</span>
          {renderToggle("ログイン状態", "auth", AUTH_OPTIONS)}
          {renderToggle("ログイン方法", "methods", METHOD_OPTIONS)}
          {renderToggle("退会の確認", "dialog", DIALOG_OPTIONS)}
        </div>
        <UserEdit
          // 比較用クエリの切替で画面の状態をリセットする
          key={`${current.auth}-${current.methods}-${current.dialog}`}
          auth={current.auth}
          methods={current.methods}
          initialWithdrawOpen={current.dialog === "withdraw"}
        />
      </main>
      <Footer />
    </>
  );
}
