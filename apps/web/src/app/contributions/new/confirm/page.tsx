// see docs/screen-list.md（S11のパスは`/contributions/new/confirm`。現時点ではMock・
// 画面遷移確認用の独立パス）, docs/decisions/DEC-0003-s04-s11-ai-analysis-boundary.md
//
// S11自体のWireframe・詳細設計はIssue #40で扱う。本ページはS04が解析完了を待たず
// 遷移すること(DEC-0003「受け取って解析する」方式)を確認するための暫定表示。
import { ConfirmDraftView } from "@/components/layout/ConfirmDraftView";

type Props = {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

export default async function ContributionConfirmPage({ searchParams }: Props) {
  const sp = await searchParams;
  const draftId = Array.isArray(sp.draft) ? sp.draft[0] : sp.draft;
  return <ConfirmDraftView draftId={draftId} />;
}
