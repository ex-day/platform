# ex-day の画面のイメージ（Issue #114）
#
# next.config.ts の output: "standalone" で作った最小のサーバー（server.js）を Node で動かす。
# API の呼び先は実行時の環境変数 EXDAY_API_BASE_URL で渡す（ビルドし直さずに変えられる）。
# Node の版は .nvmrc（24）に合わせる。

FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:24-alpine AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    HOSTNAME=0.0.0.0 \
    PORT=3000
# standalone は public と .next/static を含まないため、別にコピーする
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
COPY --from=build --chown=node:node /app/public ./public
USER node
EXPOSE 3000
CMD ["node", "server.js"]
