package com.proyecto.PlayApp.service;

import com.proyecto.PlayApp.entity.Usuario;
import com.proyecto.PlayApp.repository.UsuarioRepository;
import com.proyecto.PlayApp.service.SalesChartsCalculator.ProductInfo;
import com.proyecto.PlayApp.service.SalesChartsCalculator.SaleLine;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Datos de la pestaña "Gráficos": consulta MongoDB en cada petición (siempre datos actuales).
 * Solo se proyectan los campos necesarios; nunca se leen las imágenes de los productos.
 * Igual que el reporte existente: el administrador global ve todo y un administrador de
 * restaurante solo ve las ventas de sus propios productos.
 */
@Service
public class SalesChartsService {
    private static final String PEDIDOS = "pedidos";
    private static final String PRODUCTOS = "productos";

    private final MongoTemplate mongoTemplate;
    private final UsuarioRepository usuarioRepository;
    private final String adminGlobalCorreo;

    public SalesChartsService(MongoTemplate mongoTemplate, UsuarioRepository usuarioRepository,
                              @Value("${playapp.admin.email:adminplayapp01@gmail.com}") String adminGlobalCorreo) {
        this.mongoTemplate = mongoTemplate;
        this.usuarioRepository = usuarioRepository;
        this.adminGlobalCorreo = adminGlobalCorreo;
    }

    public Map<String, Object> build(String correoAdmin, String rango, LocalDate desde, LocalDate hasta,
                                     Integer categoria, String granularidad) {
        String restauranteId = restauranteIdSiAplica(correoAdmin);
        LocalDate hoy = LocalDate.now();
        boolean todo = rango != null && "todo".equalsIgnoreCase(rango.trim());
        LocalDate primerPedido = todo ? primerPedido(restauranteId) : null;
        SalesChartsCalculator.Range range = SalesChartsCalculator.resolveRange(rango, desde, hasta, granularidad, hoy, primerPedido);

        LocalDate inicio = range.hasPrevious() ? range.anteriorDesde() : range.desde();
        List<SaleLine> lineas = cargarLineas(inicio.atStartOfDay(), range.hasta().plusDays(1).atStartOfDay(), restauranteId);
        List<ProductInfo> catalogo = cargarProductos(restauranteId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generatedAt", LocalDateTime.now().withNano(0).toString());
        out.putAll(SalesChartsCalculator.compute(range, categoria, lineas, catalogo));
        return out;
    }

    // ---------------------------------------------------------------- consultas

    List<SaleLine> cargarLineas(LocalDateTime desde, LocalDateTime hastaExclusivo, String restauranteId) {
        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", new Document("timestamp",
                new Document("$gte", toDate(desde)).append("$lt", toDate(hastaExclusivo)))
                .append("estado", new Document("$nin", List.of(3, -1)))
                .append("carrito", new Document("$exists", true).append("$ne", List.of()))));
        pipeline.add(new Document("$unwind", new Document("path", "$carrito").append("preserveNullAndEmptyArrays", false)));
        if (restauranteId != null) {
            pipeline.add(new Document("$match", new Document("$or", List.of(
                    new Document("carrito.producto.entidad._id", restauranteId),
                    new Document("carrito.producto.entidad.id", restauranteId)))));
        }
        pipeline.add(new Document("$project", new Document("_id", 0)
                .append("pedidoId", "$_id")
                .append("timestamp", 1)
                .append("productoId", ifNull("$carrito.producto._id", ifNull("$carrito.producto.id", "$carrito.producto.nombre")))
                .append("nombre", ifNull("$carrito.producto.nombre", "Sin producto"))
                .append("categoria", "$carrito.producto.categoria")
                .append("cantidad", ifNull("$carrito.cantidad", 1))
                .append("subtotal", new Document("$cond", List.of(
                        new Document("$gt", List.of(ifNull("$carrito.subtotal", 0), 0)),
                        "$carrito.subtotal",
                        new Document("$multiply", List.of(ifNull("$carrito.producto.precio", 0), ifNull("$carrito.cantidad", 1)))
                )))));

        List<SaleLine> out = new ArrayList<>();
        for (Document d : mongoTemplate.getCollection(PEDIDOS).aggregate(pipeline)) {
            LocalDateTime ts = toLocalDateTime(d.get("timestamp"));
            if (ts == null) continue;
            out.add(new SaleLine(
                    String.valueOf(d.get("pedidoId")),
                    ts,
                    d.get("productoId") == null ? null : String.valueOf(d.get("productoId")),
                    d.get("nombre") == null ? null : String.valueOf(d.get("nombre")),
                    toInteger(d.get("categoria")),
                    toDouble(d.get("cantidad"), 1),
                    toDouble(d.get("subtotal"), 0)));
        }
        return out;
    }

    List<ProductInfo> cargarProductos(String restauranteId) {
        Document filtro = restauranteId == null ? new Document() : new Document("$or", List.of(
                new Document("entidad._id", restauranteId),
                new Document("entidad.id", restauranteId)));
        Document proyeccion = new Document("nombre", 1).append("categoria", 1).append("precio", 1).append("stock", 1);
        List<ProductInfo> out = new ArrayList<>();
        for (Document d : mongoTemplate.getCollection(PRODUCTOS).find(filtro).projection(proyeccion)) {
            out.add(new ProductInfo(
                    String.valueOf(d.get("_id")),
                    d.get("nombre") == null ? null : String.valueOf(d.get("nombre")),
                    toInteger(d.get("categoria")),
                    d.get("precio") instanceof Number n ? n.doubleValue() : null,
                    d.get("stock") instanceof Number n ? n.doubleValue() : null));
        }
        return out;
    }

    LocalDate primerPedido(String restauranteId) {
        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$match", new Document("timestamp", new Document("$ne", null))
                .append("estado", new Document("$nin", List.of(3, -1)))));
        if (restauranteId != null) {
            pipeline.add(new Document("$unwind", "$carrito"));
            pipeline.add(new Document("$match", new Document("$or", List.of(
                    new Document("carrito.producto.entidad._id", restauranteId),
                    new Document("carrito.producto.entidad.id", restauranteId)))));
        }
        pipeline.add(new Document("$sort", new Document("timestamp", 1)));
        pipeline.add(new Document("$limit", 1));
        pipeline.add(new Document("$project", new Document("_id", 0).append("timestamp", 1)));
        Document first = mongoTemplate.getCollection(PEDIDOS).aggregate(pipeline).first();
        LocalDateTime ts = first == null ? null : toLocalDateTime(first.get("timestamp"));
        return ts == null ? null : ts.toLocalDate();
    }

    private String restauranteIdSiAplica(String correoAdmin) {
        if (correoAdmin == null || adminGlobalCorreo.equalsIgnoreCase(correoAdmin.trim())) {
            return null;
        }
        Usuario usuario = usuarioRepository.findUsuarioByCorreo(correoAdmin.trim());
        return usuario == null ? null : usuario.getId();
    }

    // ---------------------------------------------------------------- conversiones

    // Spring Data guarda LocalDateTime como Date usando la zona del sistema; se lee igual.
    private static Date toDate(LocalDateTime t) {
        return Date.from(t.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDateTime toLocalDateTime(Object v) {
        if (v instanceof Date d) return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
        return null;
    }

    private static Integer toInteger(Object v) {
        return v instanceof Number n ? n.intValue() : null;
    }

    private static double toDouble(Object v, double fallback) {
        return v instanceof Number n && Double.isFinite(n.doubleValue()) ? n.doubleValue() : fallback;
    }

    private static Document ifNull(Object value, Object fallback) {
        return new Document("$ifNull", List.of(value, fallback));
    }
}
