import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  base: "/account/",
  plugins: [react()],
  build: { outDir: "../src/main/resources/static", emptyOutDir: true },
  server: { port: 5178, proxy: { "/account/api": "http://localhost:13011" } },
});
