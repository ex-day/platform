// GET /api/top → API の GET /top への中継(Issue #113、#135。src/lib/api/relay.ts)
import type { NextRequest } from "next/server";
import { fetchTop } from "@/lib/api/top";
import { relay } from "@/lib/api/relay";

export async function GET(request: NextRequest) {
  return relay(() => fetchTop(request.nextUrl.searchParams.get("from")));
}
