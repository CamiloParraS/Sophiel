FROM oven/bun:1.4.3-slim
# sqlite3 CLI only for the `fly ssh console` schema check (V8).
RUN apt-get update && apt-get install -y --no-install-recommends sqlite3 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY package.json bun.lock ./
RUN bun install --frozen-lockfile --production
COPY src ./src
ENV PORT=3000 DB_PATH=/data/sophiel.db
EXPOSE 3000
CMD ["bun", "src/index.ts"]
