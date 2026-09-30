This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

This project uses [`next/font`](https://nextjs.org/docs/app/building-your-application/optimizing/fonts) to automatically optimize and load [Geist](https://vercel.com/font), a new font family for Vercel.

## API への接続（Issue #112）

S03（`/discoveries/[id]`）は API（`apps/api`）のデータで表示する。API はサーバー側（Server Component）からだけ呼び、キャッシュはしない。

| 環境変数 | 既定値 | 説明 |
| --- | --- | --- |
| `EXDAY_API_BASE_URL` | `http://localhost:8080/api/v1` | API の呼び先（サーバー側でだけ使う。ブラウザには公開しない） |

- API の型（`src/lib/api/schema.d.ts`）は、`docs/api/openapi.yaml` から `openapi-typescript` で作り、コミットしている。`openapi.yaml` を変えたら `npm run gen:api` で作り直す。
- API の呼び出しは `src/lib/api/`（`client.ts`・`discovery.ts`）、API の型からコンポーネントの props への変換は `src/lib/api/discovery-view.ts` に置く。
- モックのデータの S03（表現案の比較用バー付き）は `/mock/discoveries/[id]`（例：`/mock/discoveries/sample-1`）に残している。

### S01 をブラウザ側から取る（Issue #113）

S01（`/`）のセクション（C11）は、ブラウザ側（Client Component）で API から取る。ブラウザは API を直接呼ばず、画面と同じオリジンの中継（Route Handler）を通す。中継先は上の `EXDAY_API_BASE_URL` を実行時に読む（ビルドし直さずに変えられる）。

| 画面側の中継 | 中継先の API |
| --- | --- |
| `GET /api/discovery-sections` | `GET /discovery-sections` |
| `GET /api/discovery-sections/{sectionKey}/discoveries` | `GET /discovery-sections/{sectionKey}/discoveries` |

- 中継は `src/app/api/` の Route Handler と `src/lib/api/relay.ts`。画面が使う API だけを置き、API 全体は公開しない。API の状態コードと本文はそのまま返し、API に届かない場合は 502 を返す。
- ブラウザ側の呼び出しは `src/lib/api/browser-client.ts`（同じ OpenAPI の型を使う）。
- モックのデータの S01（比較用バー付き）は `/mock` に残している。

## Docker で動かす（Issue #114）

リポジトリ直下の `docker compose up` で、DB・API とあわせて画面も起動する（手順はリポジトリ直下の README）。画面のイメージは [`Dockerfile`](Dockerfile) で作る。`next.config.ts` の `output: "standalone"` で作った最小のサーバー（`server.js`）を Node で動かし、`EXDAY_API_BASE_URL` に `http://api:8080/api/v1` を渡す。

## CI（Issue #116）

`apps/web/**`・`docs/api/openapi.yaml`・workflow 自身を変える PR（と `main` への push）で、GitHub Actions（`.github/workflows/web-build.yml`）が次を確かめる。Node の版は `.nvmrc` で固定している。

1. `npm ci`
2. `npm run lint`
3. `npm run build`（型検査を含む。`RouteContext` 等の型は build 時に作られるため、`tsc --noEmit` 単独では実行しない）
4. `npm run gen:api` のあと `git diff --exit-code src/lib/api/schema.d.ts`：API の型が `docs/api/openapi.yaml` とずれていれば失敗する。失敗したら `npm run gen:api` を実行し、作り直した `schema.d.ts` をコミットする。

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
