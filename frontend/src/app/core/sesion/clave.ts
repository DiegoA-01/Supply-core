/** Contraseña que acepta el backend ({@code ReglasPassword}): 8 a 72 caracteres, al menos una letra y un número. */
export const PATRON_CLAVE = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;
