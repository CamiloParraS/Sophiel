import { createApp } from "./app";

createApp({ dbPath: process.env.DB_PATH ?? "sophiel.db" }).listen({ port: Number(process.env.PORT ?? 3000), hostname: "0.0.0.0" });
