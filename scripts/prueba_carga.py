"""Prueba de carga del POS (spec 010): varias cajas vendiendo a la vez al ritmo de un mostrador con fila.

Uso (con el stack de Docker arriba y sembrado con scripts/sembrar_demo.py):
    py scripts/prueba_carga.py                        # 50 ventas por minuto durante 5 minutos en 3 cajas
    py scripts/prueba_carga.py --ritmo 50 --minutos 1 --cajas 2

Prepara sus propios cajeros y cajas (con claves aleatorias), abre un turno por caja, vende y al final comprueba:
- que no hubo errores,
- que el stock no quedó negativo y bajó exactamente lo vendido,
- que los consecutivos de cada caja no tienen huecos,
- que cada caja cierra cuadrada (lo contado es lo cobrado, diferencia cero).
El reporte queda en reporte-carga.local.json (no se sube a git). Solo usa la biblioteca estándar de Python 3.
"""
import argparse, base64, hashlib, hmac, json, secrets, statistics, string, struct, sys, threading, time, uuid
import urllib.error, urllib.request
from decimal import Decimal
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
CREDENCIALES = RAIZ / 'credenciales-docker.local.json'
REPORTE = RAIZ / 'reporte-carga.local.json'


def clave():
    """Contraseña aleatoria con letras y números."""
    alfabeto = string.ascii_letters + string.digits
    while True:
        c = ''.join(secrets.choice(alfabeto) for _ in range(16))
        if any(x.isalpha() for x in c) and any(x.isdigit() for x in c):
            return c


def totp(secreto):
    """Código de 6 dígitos de la app autenticadora para el secreto dado."""
    k = base64.b32decode(secreto.upper() + '=' * (-len(secreto) % 8))
    h = hmac.new(k, struct.pack('>Q', int(time.time()) // 30), hashlib.sha1).digest()
    o = h[-1] & 15
    return f'{(struct.unpack(">I", h[o:o + 4])[0] & 0x7fffffff) % 1000000:06d}'


class Api:
    """Cliente mínimo de la API con el token de una sesión."""

    def __init__(self, base, token=None):
        self.base = base
        self.token = token

    def llamar(self, metodo, ruta, cuerpo=None, cabeceras=None):
        """Devuelve (estado, cuerpo JSON o texto, segundos)."""
        datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
        req = urllib.request.Request(self.base + ruta, data=datos, method=metodo)
        req.add_header('Content-Type', 'application/json')
        if self.token:
            req.add_header('Authorization', 'Bearer ' + self.token)
        for k, v in (cabeceras or {}).items():
            req.add_header(k, v)
        inicio = time.perf_counter()
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                texto = r.read()
                return r.status, json.loads(texto) if texto else None, time.perf_counter() - inicio
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode(errors='replace')[:300], time.perf_counter() - inicio

    def exigir(self, paso, metodo, ruta, cuerpo=None):
        estado, r, _ = self.llamar(metodo, ruta, cuerpo)
        if estado not in (200, 201, 204):
            sys.exit(f'Falló "{paso}": {estado} {r}')
        return r


def entrar(base, empresa, correo, clave_, secreto_mfa=None):
    """Inicia sesión (con segundo factor si hace falta) y devuelve la API con el token."""
    api = Api(base)
    # El backend limita los ingresos por minuto desde una IP; la prueba entra varias veces desde el mismo equipo
    # (en la vida real cada cajero entra desde el suyo), así que ante un 429 espera el minuto y reintenta.
    for _ in range(4):
        estado, r, _ = api.llamar('POST', '/api/auth/login', {'empresa': empresa, 'correo': correo, 'password': clave_})
        if estado != 429:
            break
        print(f'  límite de ingresos por minuto alcanzado; esperando para entrar como {correo}…')
        time.sleep(61)
    if estado != 200:
        sys.exit(f'Falló "ingreso {correo}": {estado} {r}')
    if r.get('desafioMfa'):
        r = api.exigir('segundo factor ' + correo, 'POST', '/api/auth/mfa', {'desafio': r['desafioMfa'], 'codigo': totp(secreto_mfa)})
    api.token = r['token']
    return api


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--ritmo', type=int, default=50, help='ventas por minuto entre todas las cajas (50)')
    p.add_argument('--minutos', type=float, default=5, help='duración en minutos (5)')
    p.add_argument('--cajas', type=int, default=3, help='cajas vendiendo a la vez (3)')
    p.add_argument('--credenciales', default=str(CREDENCIALES), help='archivo JSON de la siembra')
    a = p.parse_args()
    c = json.loads(Path(a.credenciales).read_text(encoding='utf-8'))
    base, empresa = c['base'].rstrip('/'), c['empresa']
    total = int(a.ritmo * a.minutos)
    print(f'Prueba de carga: {total} ventas ({a.ritmo}/min durante {a.minutos} min) en {a.cajas} cajas contra {base}')

    # 1. Preparación: cajeros y cajas propios de la prueba, existencias suficientes
    admin = entrar(base, empresa, c['admin']['correo'], c['admin']['clave'], c['admin'].get('secretoMfa'))
    bodega = next(b for b in admin.exigir('bodegas', 'GET', '/api/bodegas') if b.get('activo', True))
    producto = admin.exigir('producto', 'GET', '/api/ventas/productos?q=' + c['producto'])[0]
    correo_bodega = c.get('bodeguero', 'bodeguero@ferreteriademo.local')
    bodeguero = entrar(base, empresa, correo_bodega, c['cuentas'][correo_bodega])
    bodeguero.exigir('existencias para la prueba', 'POST', '/api/inventario/movimientos', {
        'productoId': producto['productoId'], 'tipo': 'AJUSTE_POSITIVO', 'bodegaDestinoId': bodega['id'],
        'cantidad': total + 10, 'motivo': 'Existencias para la prueba de carga'})
    stock_antes = Decimal(str(admin.exigir('stock', 'GET', f'/api/productos/{producto["productoId"]}')['stockTotal']))
    marca = uuid.uuid4().hex[:4].upper()
    cajeros = []
    for i in range(a.cajas):
        correo, cl = f'carga{i + 1}.{marca.lower()}@ferreteriademo.local', clave()
        admin.exigir('cajero de prueba', 'POST', '/api/usuarios', {'nombreCompleto': f'Cajero carga {i + 1}', 'correo': correo,
                                                                    'password': cl, 'roles': ['CAJERO']})
        caja = admin.exigir('caja de prueba', 'POST', '/api/cajas', {'nombre': f'Carga {marca} {i + 1}', 'sedeId': bodega['sedeId'],
                                                                    'bodegaId': bodega['id'], 'prefijoConsecutivo': f'L{marca[:2]}{i + 1}',
                                                                    'topeEfectivo': 0})
        api = entrar(base, empresa, correo, cl)
        turno = api.exigir('abrir turno', 'POST', '/api/turnos', {'cajaId': caja['id'], 'baseApertura': 0})
        cajeros.append({'api': api, 'caja': caja, 'turno': turno, 'cobrado': Decimal('0'), 'ventas': []})

    # 2. Carga: cada caja vende a su ritmo; todas a la vez
    resultados, candado = [], threading.Lock()
    intervalo = 60.0 / a.ritmo * a.cajas
    por_caja = [total // a.cajas + (1 if i < total % a.cajas else 0) for i in range(a.cajas)]
    inicio = time.perf_counter()

    def vender(i):
        k = cajeros[i]
        for n in range(por_caja[i]):
            objetivo = inicio + i * (intervalo / a.cajas) + n * intervalo
            time.sleep(max(0.0, objetivo - time.perf_counter()))
            estado, r, seg = k['api'].llamar('POST', '/api/ventas', {
                'lineas': [{'productoId': producto['productoId'], 'cantidad': 1}],
                'pagos': [{'medio': 'EFECTIVO', 'monto': 1000000}]}, {'Idempotency-Key': str(uuid.uuid4())})
            with candado:
                resultados.append({'caja': i, 'estado': estado, 'segundos': seg, 'error': None if estado == 201 else r})
                if estado == 201:
                    k['cobrado'] += Decimal(str(r['totalAPagar']))
                    k['ventas'].append(r['consecutivo'])

    hilos = [threading.Thread(target=vender, args=(i,)) for i in range(a.cajas)]
    for h in hilos:
        h.start()
    for h in hilos:
        h.join()
    duracion = time.perf_counter() - inicio

    # 3. Comprobaciones
    errores = [r for r in resultados if r['estado'] != 201]
    tiempos = sorted(r['segundos'] for r in resultados)
    vendidas = len(resultados) - len(errores)
    stock_despues = Decimal(str(admin.exigir('stock', 'GET', f'/api/productos/{producto["productoId"]}')['stockTotal']))
    huecos, descuadres = [], []
    for k in cajeros:
        numeros = sorted(int(x.split('-')[1]) for x in k['ventas'])
        if numeros != list(range(1, len(numeros) + 1)):
            huecos.append(k['caja']['nombre'])
        cierre = k['api'].exigir('cerrar turno', 'POST', f'/api/turnos/{k["turno"]["id"]}/cerrar',
                                 {'contadoEfectivo': float(k['cobrado'])})
        if any(Decimal(str(v)) != 0 for v in (cierre.get('diferencias') or {}).values()):
            descuadres.append({'caja': k['caja']['nombre'], 'diferencias': cierre.get('diferencias')})

    reporte = {
        'ventasPedidas': total, 'ventasHechas': vendidas, 'errores': len(errores), 'cajas': a.cajas,
        'duracionSegundos': round(duracion, 1), 'ritmoLogradoPorMinuto': round(vendidas / duracion * 60, 1),
        'latenciaMediaMs': round(statistics.mean(tiempos) * 1000) if tiempos else None,
        'latenciaP95Ms': round(tiempos[int(len(tiempos) * 0.95) - 1] * 1000) if tiempos else None,
        'latenciaMaxMs': round(tiempos[-1] * 1000) if tiempos else None,
        'stockAntes': str(stock_antes), 'stockDespues': str(stock_despues),
        'stockNoNegativo': stock_despues >= 0, 'stockBajoLoVendido': stock_antes - stock_despues == vendidas,
        'consecutivosSinHuecos': not huecos, 'cajasConHuecos': huecos,
        'cajasCuadradas': not descuadres, 'descuadres': descuadres,
        'primerosErrores': [e['error'] for e in errores[:5]],
    }
    REPORTE.write_text(json.dumps(reporte, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(reporte, ensure_ascii=False, indent=2))
    ok = (not errores and reporte['stockNoNegativo'] and reporte['stockBajoLoVendido'] and not huecos and not descuadres)
    print('\nRESULTADO:', 'APROBADA' if ok else 'FALLIDA', f'(reporte en {REPORTE.name})')
    sys.exit(0 if ok else 1)


if __name__ == '__main__':
    main()
