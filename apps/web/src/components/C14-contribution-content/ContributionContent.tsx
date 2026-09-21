// see docs/ui/components/C14-contribution-content.md
import { ImageIcon, PaperclipIcon } from "lucide-react";
import type { Contribution } from "@/lib/mock-data/contribution";
import { MockLink } from "./MockLink";

const TYPE_LABEL: Record<Contribution["type"], string> = {
  knowledge: "知識",
  question: "疑問",
};

function PictureGrid({ pictures }: { pictures: Contribution["pictures"] }) {
  if (pictures.length === 0) return null;

  const [first, ...rest] = pictures;
  const visibleRest = rest.slice(0, 2);
  const overflowCount = rest.length - visibleRest.length;

  return (
    <div className="flex gap-2">
      <div className="flex h-56 flex-1 items-center justify-center gap-2 rounded-md bg-muted text-xs text-muted-foreground">
        <ImageIcon className="h-4 w-4" aria-hidden />
        {first.caption}
      </div>
      {visibleRest.length > 0 ? (
        <div className="flex w-32 flex-col gap-2">
          {visibleRest.map((picture, i) => {
            const isLast = i === visibleRest.length - 1;
            return (
              <div
                key={picture.caption}
                className="flex h-[calc((14rem-0.5rem)/2)] flex-1 items-center justify-center rounded-md bg-muted p-1 text-center text-[11px] text-muted-foreground"
              >
                {isLast && overflowCount > 0 ? `+写真${overflowCount + 1}…` : picture.caption}
              </div>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}

export function ContributionContent({ contribution }: { contribution: Contribution }) {
  const hasSpecifiedContext =
    contribution.place || contribution.time || contribution.season;

  return (
    <section className="flex flex-col gap-4 rounded-lg border p-4">
      <div className="flex items-center gap-2">
        <span className="rounded-full border px-3 py-1 text-xs font-semibold">
          {TYPE_LABEL[contribution.type]}
        </span>
      </div>
      <p className="whitespace-pre-line text-sm leading-relaxed">
        {contribution.body}
      </p>
      <PictureGrid pictures={contribution.pictures} />
      {contribution.attachment ? (
        <MockLink className="inline-flex w-fit items-center gap-1.5 text-sm text-primary underline underline-offset-2">
          <PaperclipIcon className="h-4 w-4" aria-hidden />
          {contribution.attachment.name}
        </MockLink>
      ) : null}
      {hasSpecifiedContext ? (
        <div className="flex flex-col gap-1 border-t pt-3 text-xs text-muted-foreground">
          <p className="text-[11px]">投稿者が指定した内容(後から変わる解釈とは別)</p>
          <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
            {contribution.place ? (
              <>
                <dt>場所(指定)</dt>
                <dd className="text-foreground">{contribution.place}</dd>
              </>
            ) : null}
            {contribution.time ? (
              <>
                <dt>対象時期(指定)</dt>
                <dd className="text-foreground">{contribution.time}</dd>
              </>
            ) : null}
            {contribution.season ? (
              <>
                <dt>季節(指定)</dt>
                <dd className="text-foreground">{contribution.season}</dd>
              </>
            ) : null}
          </dl>
        </div>
      ) : null}
      <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
        <span>投稿日時: {contribution.postedAt}</span>
        <span>
          投稿者:{" "}
          <MockLink className="text-primary underline underline-offset-2">
            {contribution.author}
          </MockLink>
        </span>
      </div>
    </section>
  );
}
