import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { Page, expect, test } from '@playwright/test';

/** Cuentas que deja `scripts/sembrar_demo.py` (archivo local, nunca se sube a git). */
interface Credenciales {
  empresa: string;
  /** Correo del cajero (por defecto el de la siembra). */
  cajero?: string;
  /** Correo del supervisor de ventas (por defecto el de la siembra). */
  supervisor?: string;
  cuentas: Record<string, string>;
  pinSupervisor: string;
  caja: string;
  producto: string;
}

const ARCHIVO = process.env.E2E_CREDENCIALES ?? resolve(__dirname, '..', '..', 'credenciales-docker.local.json');
const CAJERO_SIEMBRA = 'cajero@ferreteriademo.local';
const SUPERVISOR_SIEMBRA = 'supervisor.ventas@ferreteriademo.local';

/** @returns las cuentas de la demo sembrada */
function credenciales(): Credenciales {
  return JSON.parse(readFileSync(ARCHIVO, 'utf-8')) as Credenciales;
}

/**
 * Inicia sesión con el código de la empresa.
 *
 * @param page   página
 * @param c      cuentas de la demo
 * @param correo usuario
 */
async function entrar(page: Page, c: Credenciales, correo: string): Promise<void> {
  await page.goto('/ingreso');
  await page.locator('#empresa').fill(c.empresa);
  await page.locator('#correo').fill(correo);
  await page.locator('#password').fill(c.cuentas[correo]);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL(/\/app\//);
}

test.beforeEach(async ({ page }) => {
  // La impresión del tiquete abre el diálogo del sistema: en la prueba se anota en vez de imprimir
  await page.addInitScript(() => {
    (window as unknown as { __impresiones: number }).__impresiones = 0;
    const agregar = Node.prototype.appendChild;
    Node.prototype.appendChild = function <T extends Node>(this: Node, nodo: T): T {
      const r = agregar.call(this, nodo) as T;
      if (nodo instanceof HTMLIFrameElement && nodo.contentWindow) {
        nodo.contentWindow.print = () => { (window as unknown as { __impresiones: number }).__impresiones++; };
      }
      return r;
    };
  });
});

/**
 * Spec 010: el recorrido completo del mostrador en el stack de Docker.
 * Abrir turno → vender con el lector y efectivo → anular con el PIN del supervisor → cerrar a ciegas y cuadrar.
 */
test('abrir turno, vender, anular con PIN y cerrar el turno cuadrado', async ({ page }) => {
  const c = credenciales();
  const cajero = c.cajero ?? CAJERO_SIEMBRA;
  const supervisor = c.supervisor ?? SUPERVISOR_SIEMBRA;
  await entrar(page, c, cajero);

  // 1. Sin turno, el POS lleva a Mi turno: se abre con una base de $100.000
  await page.goto('/app/pos');
  await page.waitForURL(/\/app\/caja\/mi-turno/);
  const opcionCaja = page.locator('#ab-caja option', { hasText: c.caja }).first();
  await expect(opcionCaja).toBeAttached();
  await page.locator('#ab-caja').selectOption((await opcionCaja.getAttribute('value'))!);
  await page.locator('#ab-base').fill('100000');
  await page.getByRole('button', { name: 'Abrir turno' }).click();
  await expect(page.getByRole('heading', { name: 'Cierre ciego' })).toBeVisible();

  // 2. Venta: el lector tiene el foco; Enter agrega el producto y F9 abre el cobro
  await page.goto('/app/pos');
  const lector = page.locator('#lector');
  await expect(lector).toBeFocused();
  await lector.fill(c.producto);
  await lector.press('Enter');
  await expect(page.locator('.carrito tbody tr').first()).toContainText(c.producto);
  await expect(page.locator('.totales')).toContainText('Total');
  await page.keyboard.press('F9');
  const cobro = page.getByRole('dialog', { name: /Cobrar/ });
  await expect(cobro).toBeVisible();
  await cobro.getByRole('button', { name: 'Exacto' }).click();
  await expect(cobro.getByText(/Cambio/)).toBeVisible();
  await cobro.getByRole('button', { name: 'Confirmar venta' }).click();
  const ultima = page.locator('.ultima');
  await expect(ultima).toContainText(/C1-\d{6}/);
  const consecutivo = (await ultima.innerText()).match(/C1-\d{6}/)![0];
  await expect.poll(() => page.evaluate(() => (window as unknown as { __impresiones: number }).__impresiones)).toBeGreaterThan(0);

  // 3. Anular la venta en el turno con el PIN del supervisor
  await page.goto('/app/ventas');
  await page.locator('#vf-cons').fill(consecutivo);
  await page.locator('#vf-cons').press('Enter');
  await page.getByRole('button', { name: 'Detalle' }).first().click();
  const detalle = page.locator('.detalle');
  await detalle.getByRole('button', { name: 'Anular', exact: true }).click();
  await page.locator('#an-motivo').fill('Error de digitación en la prueba');
  await page.getByRole('button', { name: 'Anular (pide PIN)' }).click();
  await page.locator('#ap-correo').fill(supervisor);
  await page.locator('#ap-pin').fill(c.pinSupervisor);
  await page.getByRole('button', { name: 'Autorizar' }).click();
  await expect(detalle).toContainText('Anulada');

  // 4. Cierre ciego: la venta anulada devolvió el dinero, así que en la caja está solo la base
  await page.goto('/app/caja/mi-turno');
  await page.locator('#cc-EFECTIVO').fill('100000');
  await page.getByRole('button', { name: 'Cerrar turno' }).click();
  await expect(page.getByRole('heading', { name: 'Turno cerrado' })).toBeVisible();
  await expect(page.getByText('El turno cuadró dentro de la tolerancia.')).toBeVisible();
});
