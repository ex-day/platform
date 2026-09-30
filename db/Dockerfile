# PostgreSQL 18 + PostGIS（場所）+ pgvector（ベクトル検索。今は使わず、MVP に入れると決めたら使う）
# ex-day/poc の DB と同じ構成
FROM pgvector/pgvector:pg18

RUN apt-get update \
 && apt-get install -y --no-install-recommends postgresql-18-postgis-3 postgresql-18-postgis-3-scripts \
 && rm -rf /var/lib/apt/lists/*
