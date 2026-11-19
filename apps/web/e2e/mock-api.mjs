// ブラウザテスト(e2e)用の API の代わり。GET /api/v1/top だけを返す(Issue #135、PR #158)。
// - TOP の枠を、スクロールできる程度のカードの数で作る(本物の API・DB は使わない)。
// - Discovery の詳細(/api/v1/discoveries/{id})は、どの識別子にも同じ中身(./discovery-detail.json)を返す。
//   中身は OpenAPI の例 DiscoveryWalkApril を写したもの(詳細の画面がヘッダーごと描かれれば足りる)。
import { readFileSync } from "node:fs";
import { createServer } from "node:http";

const discoveryDetail = readFileSync(new URL("./discovery-detail.json", import.meta.url), "utf8");

const PORT = Number(process.env.MOCK_API_PORT ?? 4010);

function id(prefix, n) {
  return `${prefix}${String(n).padStart(12 - prefix.length, "0")}`;
}

function card(prefix, n, isExample) {
  const placeId = id("p", n);
  return {
    discovery: {
      id: id(prefix, n),
      title: `${prefix === "s" ? "いまの" : prefix === "n" ? "近くの" : "見本の"}発見 ${n}`,
      summary: `発見 ${n} の要約。`,
      displayPlaces: [{ id: placeId, name: `場所 ${n}` }],
    },
    nearestPlace: { id: placeId, name: `場所 ${n}` },
    distanceMeters: n * 100,
    isExample,
  };
}

const range = (count) => Array.from({ length: count }, (_, i) => i + 1);

const top = {
  now: "2026-04-20T10:00:00+09:00",
  season: "spring",
  origin: { place: { id: "a00000000001", name: "桜木町駅" }, source: "default", outsideStartArea: false },
  smallMap: { minutes: 120, trip: "round_trip" },
  areaCounts: { placeCount: 30, voicedDiscoveryCount: 12 },
  sections: [
    {
      kind: "seasonal",
      items: range(8).map((n) => ({
        card: card("s", n, false),
        value: { id: id("v", n), description: `今の時期の価値 ${n}` },
        scope: "nearby",
      })),
      hasMore: true,
    },
    { kind: "nearby_discoveries", items: range(12).map((n) => card("n", n, false)), hasMore: true },
    { kind: "examples", items: range(6).map((n) => card("e", n, true)), hasMore: false },
  ],
};

createServer((request, response) => {
  const url = new URL(request.url ?? "/", `http://localhost:${PORT}`);
  if (request.method === "GET" && url.pathname === "/api/v1/top") {
    response.writeHead(200, { "content-type": "application/json" });
    response.end(JSON.stringify(top));
    return;
  }
  if (request.method === "GET" && /^\/api\/v1\/discoveries\/[0-9A-Za-z]{12}$/.test(url.pathname)) {
    response.writeHead(200, { "content-type": "application/json" });
    response.end(discoveryDetail);
    return;
  }
  response.writeHead(404, { "content-type": "application/problem+json" });
  response.end(JSON.stringify({ title: "Not Found", status: 404 }));
}).listen(PORT, "127.0.0.1", () => console.log(`mock api listening on ${PORT}`));
