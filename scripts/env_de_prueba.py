"""Genera un .env de prueba con secretos aleatorios para levantar el stack de Docker (CI y pruebas locales).

Uso:
    py scripts/env_de_prueba.py            # crea .env si no existe
    py scripts/env_de_prueba.py --forzar   # lo reemplaza

El archivo .env nunca se sube a git (está en .gitignore). Solo usa la biblioteca estándar de Python 3.
"""
import secrets
import string
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
DESTINO = RAIZ / '.env'


def clave(largo=24):
    """Contraseña aleatoria con letras y números (al menos uno de cada uno)."""
    alfabeto = string.ascii_letters + string.digits
    while True:
        c = ''.join(secrets.choice(alfabeto) for _ in range(largo))
        if any(x.isalpha() for x in c) and any(x.isdigit() for x in c):
            return c


def main():
    if DESTINO.exists() and '--forzar' not in sys.argv:
        sys.exit(f'Ya existe {DESTINO.name}: no se toca. Use --forzar para reemplazarlo.')
    DESTINO.write_text('\n'.join([
        '# .env de PRUEBA generado por scripts/env_de_prueba.py (secretos aleatorios; no se sube a git)',
        f'MYSQL_ROOT_PASSWORD={clave()}',
        'DB_USER=supply',
        f'DB_PASSWORD={clave()}',
        f'JWT_SECRET={clave(64)}',
        'PLATAFORMA_ADMIN_CORREO=dueno@supplycore.local',
        f'PLATAFORMA_ADMIN_PASSWORD={clave()}Aa1',
        'WEB_PORT=8088',
        'PUBLIC_URL=http://localhost:8088',
        'IA_PROVEEDOR=ninguno',
        '',
    ]), encoding='utf-8')
    print(f'Listo: {DESTINO}')


if __name__ == '__main__':
    main()
