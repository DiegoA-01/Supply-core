import { defineConfig, devices } from '@playwright/test';

/**
 * Pruebas de punta a punta del POS (spec 010) contra el stack de Docker ya levantado y sembrado
 * (`docker compose up -d --build` y `py scripts/sembrar_demo.py`). La dirección sale de E2E_BASE_URL
 * (por defecto la del stack, http://localhost:8088) y las cuentas del archivo de la siembra, que nunca se sube a git.
 */
export default defineConfig({
  testDir: './pruebas',
  timeout: 120_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  // Sin reintentos: la prueba abre un turno y un reintento lo encontraría abierto (fallaría por otra razón)
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8088',
    locale: 'es-CO',
    timezoneId: 'America/Bogota',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
