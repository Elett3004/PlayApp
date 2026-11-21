package com.proyecto.PlayApp.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Cálculos de la pestaña "Gráficos" de la app móvil. No accede a la base de datos: recibe las
 * líneas de venta ya consultadas (ver {@link SalesChartsService}) para poder probarse por completo.
 *
 * Criterios (los mismos del reporte de "productos más vendidos"):
 * - una línea de venta es un producto dentro del carrito de un pedido no cancelado;
 * - ingreso de la línea = subtotal, o precio x cantidad si el subtotal no existe.
 */
public final class SalesChartsCalculator {

    public static final int MAX_BUCKETS = 400;
    public static final long MAX_DAYS = 3660;
    private static final Map<Integer, String> CATEGORIAS = new LinkedHashMap<>();
    static {
        CATEGORIAS.put(1, "Comida");
        CATEGORIAS.put(2, "Bebida");
        CATEGORIAS.put(3, "Servicio");
    }

    private SalesChartsCalculator() {
    }

    public record SaleLine(String pedidoId, LocalDateTime timestamp, String productoId, String nombre,
                           Integer categoria, double cantidad, double subtotal) {
    }

    public record ProductInfo(String id, String nombre, Integer categoria, Double precio, Double stock) {
    }

    public record Range(String rango, LocalDate desde, LocalDate hasta,
                        LocalDate anteriorDesde, LocalDate anteriorHasta, String granularidad) {
        public boolean hasPrevious() {
            return anteriorDesde != null;
        }
    }

    // ---------------------------------------------------------------- rango y agrupación temporal

    public static Range resolveRange(String rango, LocalDate desde, LocalDate hasta, String granularidad,
                                     LocalDate hoy, LocalDate primerPedido) {
        String r = rango == null || rango.isBlank() ? "30d" : rango.trim().toLowerCase(Locale.ROOT);
        LocalDate d;
        LocalDate h;
        switch (r) {
            case "7d" -> { h = hoy; d = hoy.minusDays(6); }
            case "30d" -> { h = hoy; d = hoy.minusDays(29); }
            case "90d" -> { h = hoy; d = hoy.minusDays(89); }
            case "365d" -> { h = hoy; d = hoy.minusDays(364); }
            case "todo" -> { h = hoy; d = primerPedido != null && !primerPedido.isAfter(hoy) ? primerPedido : hoy; }
            case "personalizado" -> {
                if (desde == null || hasta == null) {
                    throw new IllegalArgumentException("Indica las fechas desde y hasta.");
                }
                if (desde.isAfter(hasta)) {
                    throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la final.");
                }
                d = desde;
                h = hasta;
            }
            default -> throw new IllegalArgumentException("Rango no válido: " + rango);
        }
        long dias = ChronoUnit.DAYS.between(d, h) + 1;
        if (dias > MAX_DAYS) {
            throw new IllegalArgumentException("El rango máximo es de 10 años.");
        }
        LocalDate anteriorDesde = null;
        LocalDate anteriorHasta = null;
        if (!"todo".equals(r)) {
            anteriorHasta = d.minusDays(1);
            anteriorDesde = d.minusDays(dias);
        }
        return new Range(r, d, h, anteriorDesde, anteriorHasta, granularity(granularidad, d, h));
    }

    static String granularity(String requested, LocalDate d, LocalDate h) {
        long dias = ChronoUnit.DAYS.between(d, h) + 1;
        String g = requested == null || requested.isBlank() ? "auto" : requested.trim().toLowerCase(Locale.ROOT);
        g = switch (g) {
            case "auto" -> dias <= 31 ? "day" : dias <= 180 ? "week" : "month";
            case "day", "week", "month" -> g;
            default -> throw new IllegalArgumentException("Granularidad no válida: " + requested);
        };
        if ("day".equals(g) && dias > MAX_BUCKETS) g = "week";
        if ("week".equals(g) && dias / 7 + 2 > MAX_BUCKETS) g = "month";
        return g;
    }

    static LocalDate bucketStart(LocalDate date, String g) {
        return switch (g) {
            case "week" -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month" -> date.withDayOfMonth(1);
            default -> date;
        };
    }

    static List<LocalDate> buckets(LocalDate d, LocalDate h, String g) {
        List<LocalDate> out = new ArrayList<>();
        LocalDate cur = bucketStart(d, g);
        while (!cur.isAfter(h)) {
            out.add(cur);
            cur = switch (g) {
                case "week" -> cur.plusWeeks(1);
                case "month" -> cur.plusMonths(1);
                default -> cur.plusDays(1);
            };
        }
        return out;
    }

    static String label(LocalDate bucket, String g) {
        return "month".equals(g) ? YearMonth.from(bucket).toString() : bucket.toString();
    }

    // ---------------------------------------------------------------- cálculo principal

    private record Totals(double ingresos, double unidades, int pedidos) {
        double ticket() {
            return pedidos == 0 ? 0 : ingresos / pedidos;
        }
    }

    private static final class Series {
        final double[] ingresos;
        final double[] unidades;
        final List<Set<String>> pedidos;

        Series(int n) {
            ingresos = new double[n];
            unidades = new double[n];
            pedidos = new ArrayList<>(n);
            for (int i = 0; i < n; i++) pedidos.add(new HashSet<>());
        }
    }

    private static final class ProductAgg {
        final String id;
        final String nombre;
        final Integer categoria;
        final Double precio;
        final Double stock;
        final boolean activo;
        double unidades;
        double ingresos;
        final Set<String> pedidos = new HashSet<>();
        final double[] serieUnidades;
        final double[] serieIngresos;

        ProductAgg(String id, String nombre, Integer categoria, Double precio, Double stock, boolean activo, int n) {
            this.id = id;
            this.nombre = nombre;
            this.categoria = categoria;
            this.precio = precio;
            this.stock = stock;
            this.activo = activo;
            serieUnidades = new double[n];
            serieIngresos = new double[n];
        }
    }

    public static Map<String, Object> compute(Range range, Integer categoria, List<SaleLine> lines, List<ProductInfo> catalog) {
        if (categoria != null && !CATEGORIAS.containsKey(categoria)) {
            throw new IllegalArgumentException("Categoría no válida.");
        }
        lines = lines == null ? List.of() : lines;
        catalog = catalog == null ? List.of() : catalog;
        String g = range.granularidad();
        List<LocalDate> buckets = buckets(range.desde(), range.hasta(), g);
        Map<LocalDate, Integer> index = new HashMap<>();
        for (int i = 0; i < buckets.size(); i++) index.put(buckets.get(i), i);

        List<SaleLine> enRango = new ArrayList<>();
        List<SaleLine> anteriores = new ArrayList<>();
        for (SaleLine l : lines) {
            if (l == null || l.timestamp() == null) continue;
            LocalDate fecha = l.timestamp().toLocalDate();
            if (within(fecha, range.desde(), range.hasta())) {
                enRango.add(l);
            } else if (range.hasPrevious() && within(fecha, range.anteriorDesde(), range.anteriorHasta())) {
                anteriores.add(l);
            }
        }
        List<SaleLine> actual = filterCategory(enRango, categoria);
        List<SaleLine> anterior = filterCategory(anteriores, categoria);

        Totals tActual = totals(actual);
        Totals tAnterior = totals(anterior);
        boolean prev = range.hasPrevious();

        // Tendencia actual y periodo anterior alineado por posición
        Series serie = series(actual, buckets.size(), l -> index.get(bucketStart(l.timestamp().toLocalDate(), g)));
        List<Double> ingresosAnterior = null;
        List<Double> pedidosAnterior = null;
        List<Double> unidadesAnterior = null;
        if (prev) {
            List<LocalDate> prevBuckets = buckets(range.anteriorDesde(), range.anteriorHasta(), g);
            Map<LocalDate, Integer> prevIndex = new HashMap<>();
            for (int i = 0; i < prevBuckets.size(); i++) prevIndex.put(prevBuckets.get(i), i);
            Series sp = series(anterior, prevBuckets.size(), l -> prevIndex.get(bucketStart(l.timestamp().toLocalDate(), g)));
            ingresosAnterior = aligned(sp.ingresos, buckets.size());
            unidadesAnterior = aligned(sp.unidades, buckets.size());
            double[] cuentas = new double[prevBuckets.size()];
            for (int i = 0; i < cuentas.length; i++) cuentas[i] = sp.pedidos.get(i).size();
            pedidosAnterior = aligned(cuentas, buckets.size());
        }

        Map<String, Object> out = new LinkedHashMap<>();

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("rango", range.rango());
        filters.put("desde", range.desde().toString());
        filters.put("hasta", range.hasta().toString());
        filters.put("categoria", categoria);
        filters.put("granularidad", g);
        filters.put("anteriorDesde", prev ? range.anteriorDesde().toString() : null);
        filters.put("anteriorHasta", prev ? range.anteriorHasta().toString() : null);
        out.put("filters", filters);

        List<Map<String, Object>> categories = new ArrayList<>();
        CATEGORIAS.forEach((id, nombre) -> categories.add(Map.of("id", id, "nombre", nombre)));
        out.put("categories", categories);

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("ingresos", kpi(tActual.ingresos(), tAnterior.ingresos(), prev));
        kpis.put("pedidos", kpi(tActual.pedidos(), tAnterior.pedidos(), prev));
        kpis.put("ticketPromedio", kpi(tActual.ticket(), tAnterior.ticket(), prev));
        kpis.put("unidades", kpi(tActual.unidades(), tAnterior.unidades(), prev));
        out.put("kpis", kpis);

        Map<String, Object> trend = new LinkedHashMap<>();
        List<String> labels = new ArrayList<>();
        for (LocalDate b : buckets) labels.add(label(b, g));
        trend.put("labels", labels);
        trend.put("ingresos", rounded(serie.ingresos));
        trend.put("unidades", rounded(serie.unidades));
        List<Double> pedidosSerie = new ArrayList<>();
        for (Set<String> s : serie.pedidos) pedidosSerie.add((double) s.size());
        trend.put("pedidos", pedidosSerie);
        trend.put("ingresosAnterior", ingresosAnterior);
        trend.put("pedidosAnterior", pedidosAnterior);
        trend.put("unidadesAnterior", unidadesAnterior);
        out.put("trend", trend);

        out.put("products", products(actual, catalog, categoria, buckets.size(), tActual.pedidos(),
                l -> index.get(bucketStart(l.timestamp().toLocalDate(), g))));
        out.put("heatmap", heatmap(actual));
        out.put("categoryMix", categoryMix(enRango));

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("pedidos", tActual.pedidos());
        resumen.put("lineas", actual.size());
        out.put("resumen", resumen);
        return out;
    }

    // ---------------------------------------------------------------- auxiliares

    private static boolean within(LocalDate f, LocalDate d, LocalDate h) {
        return !f.isBefore(d) && !f.isAfter(h);
    }

    private static List<SaleLine> filterCategory(List<SaleLine> ls, Integer categoria) {
        if (categoria == null) return ls;
        List<SaleLine> out = new ArrayList<>();
        for (SaleLine l : ls) if (categoria.equals(l.categoria())) out.add(l);
        return out;
    }

    private static Totals totals(List<SaleLine> ls) {
        double ingresos = 0;
        double unidades = 0;
        Set<String> pedidos = new HashSet<>();
        for (SaleLine l : ls) {
            ingresos += l.subtotal();
            unidades += l.cantidad();
            pedidos.add(l.pedidoId());
        }
        return new Totals(ingresos, unidades, pedidos.size());
    }

    private interface BucketOf {
        Integer of(SaleLine l);
    }

    private static Series series(List<SaleLine> ls, int n, BucketOf bucketOf) {
        Series s = new Series(n);
        for (SaleLine l : ls) {
            Integer i = bucketOf.of(l);
            if (i == null) continue;
            s.ingresos[i] += l.subtotal();
            s.unidades[i] += l.cantidad();
            s.pedidos.get(i).add(l.pedidoId());
        }
        return s;
    }

    private static List<Double> aligned(double[] values, int n) {
        List<Double> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(i < values.length ? round2(values[i]) : null);
        return out;
    }

    private static Map<String, Object> kpi(double actual, double anterior, boolean prev) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("actual", round2(actual));
        m.put("anterior", prev ? round2(anterior) : null);
        m.put("cambioPct", prev && anterior > 0 ? round1((actual - anterior) / anterior * 100) : null);
        return m;
    }

    private static List<Map<String, Object>> products(List<SaleLine> actual, List<ProductInfo> catalog, Integer categoria,
                                                      int n, int totalPedidos, BucketOf bucketOf) {
        Map<String, ProductAgg> byKey = new LinkedHashMap<>();
        Map<String, String> idByName = new HashMap<>();
        for (ProductInfo p : catalog) {
            if (p == null || p.id() == null) continue;
            if (categoria != null && !categoria.equals(p.categoria())) continue;
            byKey.put(p.id(), new ProductAgg(p.id(), p.nombre() == null ? "Sin nombre" : p.nombre(), p.categoria(),
                    p.precio(), p.stock(), true, n));
            if (p.nombre() != null) idByName.putIfAbsent(normalize(p.nombre()), p.id());
        }
        for (SaleLine l : actual) {
            String key;
            if (l.productoId() != null && byKey.containsKey(l.productoId())) {
                key = l.productoId();
            } else if (l.nombre() != null && idByName.containsKey(normalize(l.nombre()))) {
                key = idByName.get(normalize(l.nombre()));
            } else {
                key = "retirado:" + (l.productoId() != null ? l.productoId() : normalize(l.nombre()));
                if (!byKey.containsKey(key)) {
                    byKey.put(key, new ProductAgg(key, l.nombre() == null ? "Sin producto" : l.nombre(),
                            l.categoria(), null, null, false, n));
                }
            }
            ProductAgg a = byKey.get(key);
            a.unidades += l.cantidad();
            a.ingresos += l.subtotal();
            a.pedidos.add(l.pedidoId());
            Integer i = bucketOf.of(l);
            if (i != null) {
                a.serieUnidades[i] += l.cantidad();
                a.serieIngresos[i] += l.subtotal();
            }
        }
        List<ProductAgg> list = new ArrayList<>(byKey.values());
        list.sort(Comparator.comparingDouble((ProductAgg a) -> a.ingresos).reversed()
                .thenComparing(Comparator.comparingDouble((ProductAgg a) -> a.unidades).reversed())
                .thenComparing(a -> a.nombre, String.CASE_INSENSITIVE_ORDER));
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProductAgg a : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id);
            m.put("nombre", a.nombre);
            m.put("categoria", a.categoria);
            m.put("categoriaNombre", CATEGORIAS.getOrDefault(a.categoria, "Otros"));
            m.put("precio", a.precio);
            m.put("stock", a.stock);
            m.put("activo", a.activo);
            m.put("unidades", round2(a.unidades));
            m.put("ingresos", round2(a.ingresos));
            m.put("pedidos", a.pedidos.size());
            m.put("ticketPromedio", a.pedidos.isEmpty() ? 0.0 : round2(a.ingresos / a.pedidos.size()));
            m.put("frecuenciaPct", totalPedidos == 0 ? 0.0 : round1(a.pedidos.size() * 100.0 / totalPedidos));
            m.put("serieUnidades", rounded(a.serieUnidades));
            m.put("serieIngresos", rounded(a.serieIngresos));
            out.add(m);
        }
        return out;
    }

    /** Matriz 7 x 24 (lunes = 0). Cada pedido se cuenta una sola vez, en el día y hora de su fecha. */
    private static Map<String, Object> heatmap(List<SaleLine> actual) {
        double[][] pedidos = new double[7][24];
        double[][] ingresos = new double[7][24];
        Set<String> contados = new HashSet<>();
        for (SaleLine l : actual) {
            int dia = l.timestamp().getDayOfWeek().getValue() - 1;
            int hora = l.timestamp().getHour();
            ingresos[dia][hora] += l.subtotal();
            if (contados.add(l.pedidoId())) pedidos[dia][hora] += 1;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pedidos", matrix(pedidos));
        m.put("ingresos", matrix(ingresos));
        return m;
    }

    /** Participación por categoría. Ignora el filtro de categoría para poder comparar entre ellas. */
    private static List<Map<String, Object>> categoryMix(List<SaleLine> enRango) {
        Map<Integer, double[]> acc = new LinkedHashMap<>();
        Map<Integer, Set<String>> pedidos = new LinkedHashMap<>();
        for (Integer id : CATEGORIAS.keySet()) {
            acc.put(id, new double[2]);
            pedidos.put(id, new HashSet<>());
        }
        for (SaleLine l : enRango) {
            int id = l.categoria() != null && CATEGORIAS.containsKey(l.categoria()) ? l.categoria() : 0;
            acc.computeIfAbsent(id, k -> new double[2]);
            pedidos.computeIfAbsent(id, k -> new HashSet<>());
            acc.get(id)[0] += l.subtotal();
            acc.get(id)[1] += l.cantidad();
            pedidos.get(id).add(l.pedidoId());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        acc.forEach((id, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("nombre", CATEGORIAS.getOrDefault(id, "Otros"));
            m.put("ingresos", round2(v[0]));
            m.put("unidades", round2(v[1]));
            m.put("pedidos", pedidos.get(id).size());
            out.add(m);
        });
        return out;
    }

    private static List<List<Double>> matrix(double[][] values) {
        List<List<Double>> out = new ArrayList<>();
        for (double[] row : values) out.add(rounded(row));
        return out;
    }

    private static List<Double> rounded(double[] values) {
        List<Double> out = new ArrayList<>(values.length);
        for (double v : values) out.add(round2(v));
        return out;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
