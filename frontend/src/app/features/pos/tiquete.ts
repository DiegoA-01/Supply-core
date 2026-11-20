import { Abono, Tiquete } from '../../core/api/modelos';

/** Nombre corto de cada medio de pago en el tiquete. */
const MEDIOS_TIQUETE: Record<string, string> = {
  EFECTIVO: 'Efectivo', TARJETA_DEBITO: 'T. débito', TARJETA_CREDITO: 'T. crédito', TRANSFERENCIA: 'Transferencia',
  BILLETERA: 'Billetera', CREDITO: 'Crédito',
};

const PESOS = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
const CANTIDAD = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 4 });

/**
 * @param texto texto
 * @returns el texto sin caracteres que el navegador lea como HTML
 */
function escapar(texto: string | null | undefined): string {
  return (texto ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
}

/**
 * @param valor valor en pesos
 * @returns valor formateado (los montos ya vienen calculados por el backend)
 */
function pesos(valor: number | null | undefined): string {
  return PESOS.format(valor ?? 0);
}

/**
 * Arma el tiquete de 80 mm para la impresora térmica (spec 005): consecutivo, NIT, impuestos discriminados por tarifa y
 * la marca COPIA en las reimpresiones. Todos los valores vienen del backend; aquí solo se acomodan.
 *
 * @param t datos del tiquete
 * @returns documento HTML completo
 */
export function htmlTiquete(t: Tiquete): string {
  const v = t.venta!;
  const lineas = (v.lineas ?? []).map(l => `
      <tr><td colspan="2">${escapar(l.nombre)}</td></tr>
      <tr><td>${CANTIDAD.format(l.cantidad ?? 0)} ${escapar(l.unidad)} × ${pesos(l.precioUnitario)}${(l.descuentoPct ?? 0) > 0 ? ` (−${CANTIDAD.format(l.descuentoPct!)} %)` : ''}</td>
          <td class="d">${pesos(l.totalLinea)}</td></tr>`).join('');
  const impuestos = (t.impuestos ?? []).map(i => `
      <tr><td>IVA ${CANTIDAD.format(i.tarifaPct ?? 0)} % · base ${pesos(i.base)}</td><td class="d">${pesos(i.impuesto)}</td></tr>`).join('');
  const pagos = (v.pagos ?? []).map(p => `
      <tr><td>${MEDIOS_TIQUETE[p.medio ?? ''] ?? escapar(p.medio)}${p.ultimos4 ? ` ****${escapar(p.ultimos4)}` : ''}</td>
          <td class="d">${pesos(p.medio === 'EFECTIVO' ? p.recibido : p.monto)}</td></tr>`).join('');
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><title>${escapar(v.consecutivo)}</title>
<style>
  @page { size: 80mm auto; margin: 3mm; }
  body { font: 12px/1.35 'Courier New', monospace; width: 74mm; margin: 0; color: #000; }
  h1 { font-size: 14px; margin: 0; text-align: center; }
  .c { text-align: center; } .d { text-align: right; white-space: nowrap; }
  table { width: 100%; border-collapse: collapse; } td { vertical-align: top; padding: 0; }
  hr { border: 0; border-top: 1px dashed #000; margin: 4px 0; }
  .total td { font-weight: bold; font-size: 14px; }
  .copia { text-align: center; font-weight: bold; font-size: 16px; border: 2px solid #000; margin: 4px 0; }
</style></head><body>
  ${t.copia ? `<div class="copia">COPIA ${t.impresion! - 1}</div>` : ''}
  <h1>${escapar(t.empresa)}</h1>
  <div class="c">NIT ${escapar(t.nit)}</div>
  ${t.direccion ? `<div class="c">${escapar(t.direccion)}</div>` : ''}
  <div class="c">${escapar(t.sede)} · ${escapar(v.caja)}</div>
  <hr>
  <div>Venta <b>${escapar(v.consecutivo)}</b>${v.estado === 'ANULADA' ? ' · ANULADA' : ''}</div>
  <div>${escapar(new Date(v.confirmadaEn!).toLocaleString('es-CO'))}</div>
  <div>Cajero: ${escapar(v.cajero)}${v.vendedor ? ` · Vendedor: ${escapar(v.vendedor)}` : ''}</div>
  <div>Cliente: ${escapar(v.cliente)} (${escapar(v.clienteDocumento)})</div>
  <hr>
  <table>${lineas}</table>
  <hr>
  <table>
    <tr><td>Subtotal</td><td class="d">${pesos(v.subtotal)}</td></tr>
    ${(v.descuentoTotal ?? 0) > 0 ? `<tr><td>Descuentos</td><td class="d">−${pesos(v.descuentoTotal)}</td></tr>` : ''}
    ${impuestos}
    ${(v.redondeo ?? 0) !== 0 ? `<tr><td>Redondeo</td><td class="d">${pesos(v.redondeo)}</td></tr>` : ''}
    <tr class="total"><td>TOTAL</td><td class="d">${pesos(v.totalAPagar)}</td></tr>
  </table>
  <hr>
  <table>${pagos}
    ${(v.cambio ?? 0) > 0 ? `<tr><td>Cambio</td><td class="d">${pesos(v.cambio)}</td></tr>` : ''}
  </table>
  <hr>
  <div class="c">Gracias por su compra</div>
  ${t.copia ? `<div class="c">Reimpreso ${escapar(new Date(t.impresoEn!).toLocaleString('es-CO'))}</div>` : ''}
</body></html>`;
}

/**
 * Recibo de abono de cartera en 80 mm (spec 007): consecutivo, cliente, valor, a qué ventas se aplicó y lo que queda.
 *
 * @param a abono con sus aplicaciones (todo calculado por el backend)
 * @returns documento HTML completo
 */
export function htmlRecibo(a: Abono): string {
  const filas = (a.aplicaciones ?? []).map(x => `<tr><td>Venta ${escapar(x.venta)}</td><td class="d">${pesos(x.valor)}</td></tr>`).join('');
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><title>${escapar(a.consecutivo)}</title>
<style>
  @page { size: 80mm auto; margin: 3mm; }
  body { font: 12px/1.35 'Courier New', monospace; width: 74mm; margin: 0; color: #000; }
  h1 { font-size: 14px; margin: 0; text-align: center; } .d { text-align: right; white-space: nowrap; }
  table { width: 100%; border-collapse: collapse; } hr { border: 0; border-top: 1px dashed #000; margin: 4px 0; }
  .total td { font-weight: bold; font-size: 14px; }
</style></head><body>
  <h1>Recibo de abono</h1>
  <div>${escapar(a.consecutivo)} · ${escapar(new Date(a.creadoEn!).toLocaleString('es-CO'))}</div>
  <div>Cliente: ${escapar(a.cliente)} (${escapar(a.documento)})</div>
  <div>Recibió: ${escapar(a.usuario)} · ${MEDIOS_TIQUETE[a.medioPago ?? ''] ?? escapar(a.medioPago)}</div>
  <hr><table>${filas}<tr class="total"><td>ABONO</td><td class="d">${pesos(a.valor)}</td></tr></table>
  <hr><table><tr><td>Saldo pendiente</td><td class="d">${pesos(a.saldoPendiente)}</td></tr></table>
</body></html>`;
}

/**
 * Imprime el tiquete desde un marco oculto, sin abrir ventanas que el navegador pueda bloquear.
 *
 * @param t datos del tiquete
 */
export function imprimirTiquete(t: Tiquete): void {
  imprimirHtml(htmlTiquete(t));
}

/**
 * Imprime un documento de 80 mm desde un marco oculto.
 *
 * @param html documento completo
 */
export function imprimirHtml(html: string): void {
  const marco = document.createElement('iframe');
  marco.setAttribute('aria-hidden', 'true');
  marco.style.cssText = 'position:fixed;width:0;height:0;border:0;right:0;bottom:0';
  document.body.appendChild(marco);
  const doc = marco.contentDocument;
  if (!doc || !marco.contentWindow) {
    marco.remove();
    return;
  }
  doc.open();
  doc.write(html);
  doc.close();
  marco.contentWindow.focus();
  marco.contentWindow.print();
  setTimeout(() => marco.remove(), 1000);
}
