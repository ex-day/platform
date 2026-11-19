// ブラウザテスト(e2e)の設定(Issue #135、PR #158)。
// 事前に `npm run build` しておく。API の代わりに e2e/mock-api.mjs を起動し、`next start` をそこへつなぐ。
import { defineConfig, devices } from "@playwright/test";

const WEB_PORT = 3100;
const API_PORT = 4010;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: "list",
  use: { baseURL: `http://127.0.0.1:${WEB_PORT}` },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: [
    {
      command: "node e2e/mock-api.mjs",
      url: `http://127.0.0.1:${API_PORT}/api/v1/top`,
      env: { MOCK_API_PORT: String(API_PORT) },
      reuseExistingServer: false,
    },
    {
      command: `npx next start -p ${WEB_PORT} -H 127.0.0.1`,
      url: `http://127.0.0.1:${WEB_PORT}/`,
      env: { EXDAY_API_BASE_URL: `http://127.0.0.1:${API_PORT}/api/v1` },
      reuseExistingServer: false,
    },
  ],
});
