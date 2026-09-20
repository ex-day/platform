// see docs/ui/components/C11-discovery-sections.md
"use client";
import { useState } from "react";
import { DiscoverySection } from "@/components/C05-discovery-section/DiscoverySection";
import { Loading } from "@/components/C09-loading/Loading";
import { TOP_SECTIONS } from "@/lib/mock-data/top";
export function DiscoverySections() {
  const [activeId, setActiveId] = useState(TOP_SECTIONS[0].id);
  const [isLoading, setLoading] = useState(false);
  const activeSection = TOP_SECTIONS.find((section) => section.id === activeId) ?? TOP_SECTIONS[0];
  return <div className="flex flex-col gap-6"><div className="flex flex-wrap items-center gap-3 rounded-md border border-dashed bg-muted/40 px-3 py-2 text-xs text-muted-foreground"><span className="font-medium text-foreground">[Mock比較用]</span><span>C09:</span><button type="button" onClick={() => setLoading(false)} className={!isLoading ? "font-semibold text-foreground underline" : "hover:underline"}>通常</button><button type="button" onClick={() => setLoading(true)} className={isLoading ? "font-semibold text-foreground underline" : "hover:underline"}>読み込み中</button><span className="ml-auto">C06の条件変更後の挙動は判断待ち</span></div>
    <div className="hidden flex-col gap-10 md:flex">{TOP_SECTIONS.map((section) => <div key={section.id}>{isLoading ? <Loading /> : <DiscoverySection section={section} />}</div>)}</div>
    <div className="md:hidden"><div role="tablist" aria-label="Discoveryセクション" className="mb-5 flex gap-6 overflow-x-auto border-b">{TOP_SECTIONS.map((section) => <button key={section.id} type="button" role="tab" aria-selected={section.id === activeId} onClick={() => setActiveId(section.id)} className={`shrink-0 border-b-2 px-1 pb-3 text-sm ${section.id === activeId ? "border-foreground font-semibold" : "border-transparent text-muted-foreground"}`}>{section.title}</button>)}</div>{isLoading ? <Loading cardCount={1} /> : <DiscoverySection section={activeSection} hideTitle />}</div>
  </div>;
}
