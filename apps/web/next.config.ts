import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Docker(apps/web/Dockerfile)で Node のサーバーとして動かすため、最小の構成(.next/standalone)を作る(Issue #114)
  output: "standalone",
  // S04(知識・疑問登録／編集)・S11(知識・疑問登録確認画面)は廃止した(Issue #67、docs/screen-list.md)。
  // 新規投稿はC27(新しい話を始める)の/posts/newへ、編集はS03の会話の中での編集に置き換える。
  // 旧URLはモック上の参照(S05のC16・C18等。#68の判断待ち)が残るため、リダイレクトで受ける。
  async redirects() {
    return [
      { source: "/contributions/new", destination: "/posts/new", permanent: false },
      { source: "/contributions/new/confirm", destination: "/posts/new", permanent: false },
      // モックでは投稿先のDiscoveryを引けないため、固定のS03へ移す
      { source: "/contributions/:id/edit", destination: "/mock/discoveries/sample-1", permanent: false },
    ];
  },
};

export default nextConfig;
