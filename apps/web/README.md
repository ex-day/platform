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

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
