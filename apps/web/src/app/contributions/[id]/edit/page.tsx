// see docs/ui/screens/S04-contribution-save.md
//
// モック用の仮ルート。[id]はモック用の仮ID(例: sample-1, sample-2)。
// 未知のidはsample-1相当の内容にフォールバックする(getEditableDraft)。
import { ContributionEditor } from "@/components/layout/ContributionEditor";
import { getEditableDraft } from "@/lib/mock-data/contribution-draft";

type Props = {
  params: Promise<{ id: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

export default async function ContributionEditPage({ params, searchParams }: Props) {
  const { id } = await params;
  const sp = await searchParams;
  const resume = Array.isArray(sp.resume) ? sp.resume[0] : sp.resume;

  const draft = getEditableDraft(id);

  return <ContributionEditor initialDraft={draft} resumeHandoffId={resume} />;
}
