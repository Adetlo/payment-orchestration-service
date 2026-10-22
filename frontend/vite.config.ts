/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const backend = process.env.VITE_PROXY_TARGET ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    strictPort: false,
    proxy: {
      "/api": { target: backend, changeOrigin: true },
      "/actuator": { target: backend, changeOrigin: true },
    },
  },
  build: {
    sourcemap: false,
    chunkSizeWarningLimit: 500,
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ["react", "react-dom", "react-router-dom"],
        },
      },
    },
  },
  test: {
    environment: "jsdom",
    globals: false,
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    css: false,
  },
});
