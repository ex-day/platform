"use client";

// モック用の遷移しないリンク。onClickはServer Componentから渡せないためClient Componentに分離する。
import type { ComponentProps } from "react";

export function MockLink(props: Omit<ComponentProps<"a">, "href" | "onClick">) {
  return <a {...props} href="#" onClick={(event) => event.preventDefault()} />;
}
