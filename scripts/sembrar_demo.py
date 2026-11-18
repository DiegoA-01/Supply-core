"""Siembra una empresa de demostración en el stack de Docker, usando solo la API pública (como un navegador).

Uso (con el stack arriba y un .env en la raíz):
    py scripts/sembrar_demo.py

Crea, con contraseñas aleatorias fuertes: la empresa, su administrador, un jefe de compras, un jefe de bodega,
un bodeguero, un solicitante, un aprobador, un comprador, tesorería, un auditor y un proveedor habilitado, más
sede, centro de costo, bodega, productos con existencias y políticas de aprobación.

Las claves y los secretos de la app autenticadora se guardan en `credenciales-docker.local.txt` (ignorado por git).
Solo usa la biblioteca estándar de Python 3.
"""
import base64, hashlib, hmac, json, secrets, string, struct, sys, time, urllib.error, urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
SALIDA = RAIZ / 'credenciales-docker.local.txt'
ENV = dict(l.split('=', 1) for l in (RAIZ / '.env').read_text(encoding='utf-8').splitlines() if '=' in l and not l.startswith('#'))
BASE = ENV.get('PUBLIC_URL', 'http://localhost:' + ENV.get('WEB_PORT', '8088')).rstrip('/')
CODIGO_EMPRESA = 'Ferreteria Demo'


def clave():
    """Contraseña aleatoria de 16 caracteres con letras y números."""
    alfabeto = string.ascii_letters + string.digits
    while True:
        c = ''.join(secrets.choice(alfabeto) for _ in range(16))
        if any(x.isalpha() for x in c) and any(x.isdigit() for x in c):
            return c


def totp(secreto):
    k = base64.b32decode(secreto.upper() + '=' * (-len(secreto) % 8))
    h = hmac.new(k, struct.pack('>Q', int(time.time()) // 30), hashlib.sha1).digest()
    o = h[-1] & 15
    return f'{(struct.unpack(">I", h[o:o + 4])[0] & 0x7fffffff) % 1000000:06d}'


def llamar(metodo, ruta, cuerpo=None, token=None):
    cab = {'Content-Type': 'application/json'}
    if token:
        cab['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + ruta, data=json.dumps(cuerpo).encode() if cuerpo is not None else None, headers=cab, method=metodo)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            b = r.read()
            return r.status, json.loads(b) if b else None
    except urllib.error.HTTPError as e:
        b = e.read()
        try:
            return e.code, json.loads(b)
        except Exception:
            return e.code, b


def exigir(paso, respuesta, validos=(200, 201, 204)):
    s, d = respuesta
    if s not in validos:
        sys.exit(f'FALLA en "{paso}": HTTP {s} {str(d)[:300]}')
    print('OK  ', paso)
    return d


def entrar_con_mfa(ruta_login, ruta_mfa, cuerpo, campo_desafio, secreto_conocido=None):
    """Login + segundo factor. Devuelve (token, secreto vinculado o None)."""
    d = exigir('login ' + ruta_login, llamar('POST', ruta_login, cuerpo))
    secreto = d.get('secretoMfa') or secreto_conocido
    d = exigir('segundo factor ' + ruta_mfa, llamar('POST', ruta_mfa, {'desafio' if campo_desafio == 'desafio' else 'desafio': d[campo_desafio], 'codigo': totp(secreto)}))
    return d['token'], secreto


def main():
    if SALIDA.exists():
        sys.exit(f'Ya existe {SALIDA.name}: la demo ya se sembró. Para repetir, haz "docker compose down -v", levanta de nuevo y borra ese archivo.')

    # 1. Dueño del software
    correo_dueno, clave_dueno = ENV['PLATAFORMA_ADMIN_CORREO'], ENV['PLATAFORMA_ADMIN_PASSWORD']
    tok_dueno, secreto_dueno = entrar_con_mfa('/api/plataforma/auth/login', '/api/plataforma/auth/mfa',
                                              {'correo': correo_dueno, 'password': clave_dueno}, 'desafio')

    # 2. Empresa cliente con su administrador
    admin_correo, admin_clave = 'admin@ferreteriademo.local', clave()
    exigir('crear empresa', llamar('POST', '/api/plataforma/empresas', {
        'razonSocial': 'Ferretería Demo S.A.S.', 'nit': '901234567', 'codigoPublico': CODIGO_EMPRESA,
        'planCodigo': 'EMPRESARIAL', 'estadoInicial': 'ACTIVA',
        'adminNombre': 'Dueño Ferretería', 'adminCorreo': admin_correo, 'adminPassword': admin_clave}, tok_dueno))
    tok_admin, secreto_admin = entrar_con_mfa('/api/auth/login', '/api/auth/mfa',
                                              {'empresa': CODIGO_EMPRESA, 'correo': admin_correo, 'password': admin_clave}, 'desafioMfa')

    # El dueño de la ferretería también decide sobre proveedores nuevos: además de administrar, lleva compras
    lista = exigir('usuarios de la empresa', llamar('GET', '/api/usuarios?size=50', token=tok_admin))
    yo = next(u for u in (lista['contenido'] if isinstance(lista, dict) else lista) if u['correo'] == admin_correo)
    exigir('el dueño también administra compras', llamar('PUT', f'/api/usuarios/{yo["id"]}', {
        'nombreCompleto': yo['nombreCompleto'], 'correo': yo['correo'], 'roles': ['ADMIN_SISTEMA', 'ADMIN_COMPRAS']}, tok_admin))

    # 3. Estructura de la empresa
    sede = exigir('sede', llamar('POST', '/api/sedes', {'nombre': 'Sede principal', 'direccion': 'Calle 10 # 20-30'}, tok_admin))
    cc = exigir('centro de costo', llamar('POST', '/api/centros-costo', {'codigo': 'OBRAS', 'nombre': 'Obras y mantenimiento'}, tok_admin))
    bodega = exigir('bodega', llamar('POST', '/api/bodegas', {'nombre': 'Bodega principal', 'direccion': 'Sede principal', 'sedeId': sede['id']}, tok_admin))

    # 4. Usuarios por rol
    cuentas = [
        ('Jefe de compras', 'jefe.compras@ferreteriademo.local', ['ADMIN_COMPRAS']),
        ('Jefe de bodega', 'jefe.bodega@ferreteriademo.local', ['ALMACENISTA', 'AUDITOR']),
        ('Bodeguero', 'bodeguero@ferreteriademo.local', ['ALMACENISTA']),
        ('Solicitante', 'solicitante@ferreteriademo.local', ['SOLICITANTE']),
        ('Aprobador', 'aprobador@ferreteriademo.local', ['APROBADOR']),
        ('Comprador', 'comprador@ferreteriademo.local', ['COMPRADOR']),
        ('Tesorería', 'tesoreria@ferreteriademo.local', ['ADMINISTRATIVO']),
        ('Auditor', 'auditor@ferreteriademo.local', ['AUDITOR']),
    ]
    claves = {}
    for nombre, correo, roles in cuentas:
        claves[correo] = clave()
        exigir('usuario ' + correo, llamar('POST', '/api/usuarios', {
            'nombreCompleto': nombre, 'correo': correo, 'password': claves[correo], 'roles': roles, 'centroCostoId': cc['id']}, tok_admin))

    def sesion(correo):
        d = exigir('login ' + correo, llamar('POST', '/api/auth/login', {'empresa': CODIGO_EMPRESA, 'correo': correo, 'password': claves[correo]}))
        return d['token']

    # 5. Catálogo y existencias: el jefe de compras crea productos, el bodeguero mete el stock inicial
    tok_compras = sesion('jefe.compras@ferreteriademo.local')
    tok_bodega = sesion('bodeguero@ferreteriademo.local')
    categorias = {n: exigir('categoría ' + n, llamar('POST', '/api/categorias', {'nombre': n}, tok_compras))['id']
                  for n in ('Construcción', 'Eléctrico', 'Pintura', 'Herramientas')}
    unidad = exigir('unidades', llamar('GET', '/api/unidades-medida', token=tok_compras))[0]['id']
    # código, nombre, categoría, costo, entra, sale
    productos = [
        ('CEM-50', 'Cemento gris 50 kg', 'Construcción', 32000, 300, 280),
        ('TOR-01', 'Tornillo 1 pulgada (caja)', 'Herramientas', 4500, 1000, 500),
        ('CAB-12', 'Cable 12 AWG (metro)', 'Eléctrico', 2800, 200, 40),
        ('PIN-20', 'Pintura blanca 20 L', 'Pintura', 185000, 50, 0),
        ('BRO-03', 'Brocha 3 pulgadas', 'Pintura', 9000, 30, 0),
        ('BRK-20', 'Breaker 20 A', 'Eléctrico', 21000, 10, 10),
        ('GUA-01', 'Guantes de carnaza', 'Herramientas', 7500, 120, 100),
        ('LAM-09', 'Lámpara LED 9 W', 'Eléctrico', 6800, 60, 6),
    ]
    for codigo, nombre, cat, costo, entra, sale in productos:
        p = exigir('producto ' + codigo, llamar('POST', '/api/productos', {
            'codigo': codigo, 'nombre': nombre, 'categoriaId': categorias[cat], 'unidadMedidaId': unidad,
            'stockMinimo': 5, 'costoReferencia': costo}, tok_compras))
        exigir('existencia inicial ' + codigo, llamar('POST', '/api/inventario/movimientos', {
            'productoId': p['id'], 'tipo': 'AJUSTE_POSITIVO', 'bodegaDestinoId': bodega['id'],
            'cantidad': entra, 'motivo': 'Inventario inicial de la demostración'}, tok_bodega))
        if sale:
            exigir('salida de ' + codigo, llamar('POST', '/api/inventario/movimientos', {
                'productoId': p['id'], 'tipo': 'SALIDA_CONSUMO', 'bodegaOrigenId': bodega['id'], 'cantidad': sale}, tok_bodega))

    # 6. Políticas de aprobación (quién aprueba cada solicitud y cada adjudicación)
    for tipo in ('SOLICITUD', 'ADJUDICACION'):
        exigir('política ' + tipo, llamar('POST', '/api/politicas-aprobacion', {
            'tipo': tipo, 'rolAprobador': 'APROBADOR', 'montoDesde': 0}, tok_compras))

    # 7. Un proveedor: se autorregistra y el jefe de compras lo habilita
    prov_correo, prov_clave = 'proveedor@electrosur.local', clave()
    from urllib.parse import quote
    exigir('registro del proveedor', llamar('POST', '/api/portal/registro/' + quote(CODIGO_EMPRESA, safe=''), {
        'razonSocial': 'Electro Sur Ltda.', 'nit': '800123456-1', 'telefono': '3001234567', 'direccion': 'Carrera 5 # 6-7',
        'categoriaIds': [categorias['Eléctrico'], categorias['Herramientas']], 'nombreContacto': 'Marta Rojas',
        'correo': prov_correo, 'password': prov_clave}))
    lista = exigir('proveedores pendientes', llamar('GET', '/api/proveedores?estado=PENDIENTE', token=tok_compras))
    pendientes = lista['contenido'] if isinstance(lista, dict) else lista
    exigir('habilitar proveedor', llamar('PATCH', f'/api/proveedores/{pendientes[0]["id"]}/habilitar', token=tok_compras))

    # 8. Archivo local de credenciales (nunca se sube a git)
    filas = ''.join(f'{n:<18}{c:<40}{claves[c]}\n' for n, c, _ in cuentas)
    SALIDA.write_text(f"""CUENTAS DE LA DEMO EN DOCKER - archivo local, NO se sube a git (credenciales-docker.local.txt)
Aplicación: {BASE}

== DUEÑO DEL SOFTWARE (zona /plataforma) ==
Entrada: {BASE}/plataforma
Correo:  {correo_dueno}
Clave:   la de PLATAFORMA_ADMIN_PASSWORD en el archivo .env
Segundo factor (obligatorio): agrega esta llave en tu app autenticadora (tipo "clave de configuración", basada en tiempo):
  {secreto_dueno}

== EMPRESA: {CODIGO_EMPRESA} (código de empresa para entrar a /app) ==
Dueño de la ferretería (administrador de la empresa, ADMIN_SISTEMA)
Correo:  {admin_correo}
Clave:   {admin_clave}
Segundo factor (obligatorio para este rol): llave para la app autenticadora:
  {secreto_admin}

Usuarios con rol (sin segundo factor; entran con código de empresa, correo y clave)
{'Rol':<18}{'Correo':<40}Clave
{filas}
== PROVEEDOR (portal, zona /portal; mismo código de empresa) ==
Correo:  {prov_correo}
Clave:   {prov_clave}
""", encoding='utf-8')
    print(f'\nListo. Credenciales guardadas en {SALIDA}')


if __name__ == '__main__':
    main()
