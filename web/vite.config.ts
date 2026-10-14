import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // In development the API runs on its own port. The proxy keeps the web and the API on one origin, as in production.
  server: {
    proxy: { "/api": "http://localhost:8080" },
  },
  resolve: {
    alias: {
      "@app": "/src/app",
      "@shared": "/src/shared",
      "@entities": "/src/entities",
      "@features": "/src/features",
      "@widgets": "/src/widgets",
      "@pages": "/src/pages",
    },
  },
});
