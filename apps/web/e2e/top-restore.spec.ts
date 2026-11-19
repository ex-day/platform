// S01(TOP)から Discovery の詳細へ移って戻ったときに、取得済みの TOP・選択中のタブ・スクロール位置が戻ることを確かめる
// (DEC-0022 決定4、C11。Issue #135、PR #158)。API は e2e/mock-api.mjs。
import { expect, test, type Page } from "@playwright/test";

/** ブラウザが /api/top を取った回数を数える */
function countTopRequests(page: Page) {
  const counter = { count: 0 };
  page.on("request", (request) => {
    if (new URL(request.url()).pathname === "/api/top") counter.count += 1;
  });
  return counter;
}

async function scrollTo(page: Page, y: number) {
  await page.evaluate((top) => window.scrollTo(0, top), y);
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(y);
}

/** カードを画面に入れてから(押すときに動かないように)、その時点のスクロール位置を返す */
async function positionBeforeClick(page: Page, link: ReturnType<Page["getByRole"]>) {
  await link.scrollIntoViewIfNeeded();
  // スクロール位置の保存(requestAnimationFrame の後)を待つ
  await page.waitForTimeout(200);
  return page.evaluate(() => window.scrollY);
}

test("モバイル：詳細から戻ると、取り直さずに選択中のタブとスクロール位置が戻る", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const requests = countTopRequests(page);
  await page.goto("/");
  await page.getByRole("tab", { name: "近くの発見" }).click();
  await expect(page.getByRole("tab", { name: "近くの発見" })).toHaveAttribute("aria-selected", "true");
  await scrollTo(page, 900);

  const panel = page.getByRole("tabpanel");
  const link = panel.getByRole("link", { name: /近くの発見 6/ });
  const before = await positionBeforeClick(page, link);
  expect(before).toBeGreaterThan(0);
  await link.click();
  await expect(page).toHaveURL(/\/discoveries\/n00000000006$/);

  await page.goBack();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("tab", { name: "近くの発見" })).toHaveAttribute("aria-selected", "true");
  await expect(page.getByRole("tabpanel").getByRole("link").first()).toContainText("近くの発見 1");
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(before);
  expect(requests.count).toBe(1);
});

test("PC：詳細から戻ると、取り直さずにスクロール位置が戻り、再読み込みでは取り直す", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  const requests = countTopRequests(page);
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "近くの発見", exact: true })).toBeVisible();
  await scrollTo(page, 1200);

  const link = page.getByRole("link", { name: /近くの発見 9/ });
  const before = await positionBeforeClick(page, link);
  expect(before).toBeGreaterThan(0);
  await link.click();
  await expect(page).toHaveURL(/\/discoveries\/n00000000009$/);

  await page.goBack();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole("heading", { name: "近くの発見", exact: true })).toBeVisible();
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(before);
  expect(requests.count).toBe(1);

  // 再読み込みは新しく開いたものとして取り直す
  await page.reload();
  await expect(page.getByRole("heading", { name: "近くの発見", exact: true })).toBeVisible();
  expect(requests.count).toBe(2);
});
