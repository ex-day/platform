// GET /api/discovery-sections → API の GET /discovery-sections への中継(Issue #113。src/lib/api/relay.ts)
import { fetchDiscoverySections } from "@/lib/api/discovery-sections";
import { relay } from "@/lib/api/relay";

export async function GET() {
  return relay(fetchDiscoverySections);
}
