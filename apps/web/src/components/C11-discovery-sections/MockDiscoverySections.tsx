// see docs/ui/components/C11-discovery-sections.md
//
// モックのデータ(@/lib/mock-data/top)の C11。表現案の比較用に /mock に残している(#113)。
// API のデータの C11 は ./DiscoverySections.tsx。
"use client";

import { useRef, useState, type KeyboardEvent } from "react";
import { DiscoverySection } from "@/components/C05-discovery-section/DiscoverySection";
import { Loading } from "@/components/C09-loading/Loading";
import { TOP_SECTIONS } from "@/lib/mock-data/top";

export function MockDiscoverySections() {
  const [activeId, setActiveId] = useState(TOP_SECTIONS[0].id);
  const [isLoading, setLoading] = useState(false);
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const activeSection = TOP_SECTIONS.find((section) => section.id === activeId) ?? TOP_SECTIONS[0];

  function selectAdjacentTab(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    let nextIndex: number | undefined;
    if (event.key === "ArrowRight") nextIndex = (index + 1) % TOP_SECTIONS.length;
    if (event.key === "ArrowLeft") nextIndex = (index - 1 + TOP_SECTIONS.length) % TOP_SECTIONS.length;
    if (event.key === "Home") nextIndex = 0;
    if (event.key === "End") nextIndex = TOP_SECTIONS.length - 1;
    if (nextIndex === undefined) return;
    event.preventDefault();
    setActiveId(TOP_SECTIONS[nextIndex].id);
    tabRefs.current[nextIndex]?.focus();
  }

  return <div className="flex flex-col gap-6">
    <div className="flex flex-wrap items-center gap-3 rounded-md border border-dashed bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
      <span className="font-medium text-foreground">[Mock比較用]</span><span>C09:</span>
      <button type="button" onClick={() => setLoading(false)} className={!isLoading ? "font-semibold text-foreground underline" : "hover:underline"}>通常</button>
      <button type="button" onClick={() => setLoading(true)} className={isLoading ? "font-semibold text-foreground underline" : "hover:underline"}>読み込み中</button>
      <span className="ml-auto">C06の条件変更後の挙動は判断待ち</span>
    </div>

    <div className="hidden flex-col gap-10 md:flex">
      {TOP_SECTIONS.map((section) => <div key={section.id}>{isLoading ? <Loading /> : <DiscoverySection section={section} moreHref={`/discoveries?section=${section.id}`} />}</div>)}
    </div>

    <div className="md:hidden">
      <div role="tablist" aria-label="Discoveryセクション" className="mb-5 flex gap-6 overflow-x-auto border-b">
        {TOP_SECTIONS.map((section, index) => {
          const selected = section.id === activeId;
          return <button
            key={section.id}
            ref={(node) => { tabRefs.current[index] = node; }}
            id={`${section.id}-tab`}
            type="button"
            role="tab"
            data-state={selected ? "active" : "inactive"}
            aria-selected={selected}
            aria-controls="discovery-section-panel"
            tabIndex={selected ? 0 : -1}
            onClick={() => setActiveId(section.id)}
            onKeyDown={(event) => selectAdjacentTab(event, index)}
            className={`shrink-0 rounded-t-md border-b-4 px-3 py-2 text-sm transition-colors ${selected ? "border-foreground bg-muted font-bold text-foreground" : "border-transparent text-muted-foreground hover:bg-muted/60 hover:text-foreground"}`}
          >{section.title}</button>;
        })}
      </div>
      <div id="discovery-section-panel" role="tabpanel" aria-labelledby={`${activeSection.id}-tab`} tabIndex={0}>
        {isLoading ? <Loading cardCount={1} /> : <DiscoverySection section={activeSection} hideTitle moreHref={`/discoveries?section=${activeSection.id}`} />}
      </div>
    </div>
  </div>;
}
