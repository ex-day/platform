// 書き込みの操作(投稿・返信・リアクション・資料の提供など)の扱いを切り替える。
//
// API は今は取得だけのため(docs/api/openapi.yaml、#89)、API のデータで表示する画面では
// NotReadyWriteActions で囲み、書き込みの操作を押すと「準備中」と知らせるだけにする(Issue #112)。
// 囲まない画面(モック)では、これまでどおりコンポーネントの状態として動く。
"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";

type WriteActions = {
  /** 書き込みの操作が準備中か */
  notReady: boolean;
  /** 書き込みの操作を行う。準備中なら「準備中」と知らせ、actionは呼ばない */
  run: (action: () => void) => void;
};

const MOCK_WRITE_ACTIONS: WriteActions = { notReady: false, run: (action) => action() };

const WriteActionsContext = createContext<WriteActions>(MOCK_WRITE_ACTIONS);

export function useWriteActions(): WriteActions {
  return useContext(WriteActionsContext);
}

const NOTICE_MS = 2500;

export function NotReadyWriteActions({ children }: { children: ReactNode }) {
  const [visible, setVisible] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => {
    if (timer.current) clearTimeout(timer.current);
  }, []);

  const run = useCallback(() => {
    setVisible(true);
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => setVisible(false), NOTICE_MS);
  }, []);

  return (
    <WriteActionsContext.Provider value={{ notReady: true, run }}>
      {children}
      <div
        role="status"
        aria-live="polite"
        className="pointer-events-none fixed inset-x-0 bottom-6 z-50 flex justify-center px-4"
      >
        {visible ? (
          <p className="rounded-md bg-foreground px-4 py-2 text-sm text-background shadow-lg">
            この操作は準備中です
          </p>
        ) : null}
      </div>
    </WriteActionsContext.Provider>
  );
}
