package com.proyecto.supply_core.common.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Días hábiles en Colombia: de lunes a viernes, sin los festivos nacionales (Ley 51 de 1983, "ley Emiliani").
 * <p>Los proveedores dan sus plazos en días hábiles ("llega en 6 a 7 días hábiles"); esta clase los convierte en
 * fechas reales. Festivos incluidos:</p>
 * <ul>
 *   <li>Fijos: 1 de enero, 1 de mayo, 20 de julio, 7 de agosto, 8 y 25 de diciembre.</li>
 *   <li>Trasladables al lunes siguiente: Reyes (6 ene), San José (19 mar), San Pedro y San Pablo (29 jun),
 *       Asunción (15 ago), Día de la Raza (12 oct), Todos los Santos (1 nov), Independencia de Cartagena (11 nov).</li>
 *   <li>Según la Pascua: Jueves y Viernes Santo; Ascensión, Corpus Christi y Sagrado Corazón (trasladados a lunes).</li>
 * </ul>
 */
public final class DiasHabiles {

    /** Festivos que caen siempre en su fecha. */
    private static final List<MonthDay> FIJOS = List.of(MonthDay.of(1, 1), MonthDay.of(5, 1), MonthDay.of(7, 20),
            MonthDay.of(8, 7), MonthDay.of(12, 8), MonthDay.of(12, 25));
    /** Festivos que se pasan al lunes siguiente si no caen en lunes. */
    private static final List<MonthDay> TRASLADABLES = List.of(MonthDay.of(1, 6), MonthDay.of(3, 19),
            MonthDay.of(6, 29), MonthDay.of(8, 15), MonthDay.of(10, 12), MonthDay.of(11, 1), MonthDay.of(11, 11));
    /** Festivos ya calculados por año (se calculan una sola vez). */
    private static final Map<Integer, Set<LocalDate>> CACHE = new ConcurrentHashMap<>();

    private DiasHabiles() {
    }

    /**
     * @param fecha día a revisar
     * @return {@code true} si es lunes a viernes y no es festivo en Colombia
     */
    public static boolean esHabil(LocalDate fecha) {
        DayOfWeek d = fecha.getDayOfWeek();
        return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY && !esFestivo(fecha);
    }

    /**
     * @param fecha día a revisar
     * @return {@code true} si es festivo nacional en Colombia
     */
    public static boolean esFestivo(LocalDate fecha) {
        return CACHE.computeIfAbsent(fecha.getYear(), DiasHabiles::festivosDe).contains(fecha);
    }

    /**
     * Fecha a la que se llega contando días hábiles hacia adelante, sin contar el día de partida.
     * Ejemplo: un pedido del viernes con plazo de 1 día hábil llega el lunes (si no es festivo).
     *
     * @param desde día de partida
     * @param dias  días hábiles a sumar (0 devuelve {@code desde})
     * @return fecha resultante
     * @throws IllegalArgumentException si {@code dias} es negativo
     */
    public static LocalDate sumar(LocalDate desde, int dias) {
        if (dias < 0) {
            throw new IllegalArgumentException("Los días hábiles a sumar no pueden ser negativos");
        }
        LocalDate fecha = desde;
        int contados = 0;
        while (contados < dias) {
            fecha = fecha.plusDays(1);
            if (esHabil(fecha)) {
                contados++;
            }
        }
        return fecha;
    }

    /**
     * Fecha a la que se llega contando días hábiles hacia atrás, sin contar el día de partida.
     *
     * @param desde día de partida
     * @param dias  días hábiles a restar (0 devuelve {@code desde})
     * @return fecha resultante
     * @throws IllegalArgumentException si {@code dias} es negativo
     */
    public static LocalDate restar(LocalDate desde, int dias) {
        if (dias < 0) {
            throw new IllegalArgumentException("Los días hábiles a restar no pueden ser negativos");
        }
        LocalDate fecha = desde;
        int contados = 0;
        while (contados < dias) {
            fecha = fecha.minusDays(1);
            if (esHabil(fecha)) {
                contados++;
            }
        }
        return fecha;
    }

    /**
     * Días hábiles que hay después de {@code desde} y hasta {@code hasta} (incluido). Un pedido hecho el lunes y
     * recibido el miércoles tardó 2 días hábiles.
     *
     * @param desde primer día (no se cuenta)
     * @param hasta último día (se cuenta si es hábil)
     * @return días hábiles, 0 si {@code hasta} no es posterior a {@code desde}
     */
    public static int entre(LocalDate desde, LocalDate hasta) {
        int dias = 0;
        for (LocalDate f = desde.plusDays(1); !f.isAfter(hasta); f = f.plusDays(1)) {
            if (esHabil(f)) {
                dias++;
            }
        }
        return dias;
    }

    /**
     * Días de calendario entre dos fechas (puede ser negativo si {@code hasta} es anterior).
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return días de calendario
     */
    public static long calendario(LocalDate desde, LocalDate hasta) {
        return ChronoUnit.DAYS.between(desde, hasta);
    }

    /**
     * Festivos nacionales de un año.
     *
     * @param anio año
     * @return fechas festivas
     */
    private static Set<LocalDate> festivosDe(int anio) {
        Set<LocalDate> festivos = new HashSet<>();
        FIJOS.forEach(f -> festivos.add(f.atYear(anio)));
        TRASLADABLES.forEach(f -> festivos.add(aLunes(f.atYear(anio))));
        LocalDate pascua = pascua(anio);
        festivos.add(pascua.minusDays(3));            // Jueves Santo
        festivos.add(pascua.minusDays(2));            // Viernes Santo
        festivos.add(aLunes(pascua.plusDays(39)));    // Ascensión del Señor
        festivos.add(aLunes(pascua.plusDays(60)));    // Corpus Christi
        festivos.add(aLunes(pascua.plusDays(68)));    // Sagrado Corazón
        return festivos;
    }

    /**
     * @param fecha festivo trasladable
     * @return la misma fecha si es lunes; si no, el lunes siguiente
     */
    private static LocalDate aLunes(LocalDate fecha) {
        return fecha.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Domingo de Pascua del calendario gregoriano (algoritmo anónimo de Meeus/Jones/Butcher).
     *
     * @param anio año
     * @return domingo de Pascua
     */
    static LocalDate pascua(int anio) {
        int a = anio % 19;
        int b = anio / 100;
        int c = anio % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(anio, mes, dia);
    }
}
