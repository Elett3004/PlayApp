import json
import sys

from pyomo.environ import ConcreteModel, Constraint, NonNegativeReals, Objective, SolverFactory, Var, maximize, value


def pesos(valor):
    return f"${valor:,.0f}".replace(",", ".")


def numero(datos, llave, predeterminado):
    valor = datos.get(llave, predeterminado)
    if valor is None or valor == "":
        return predeterminado
    return float(valor)


def resolver(datos):
    presupuesto = numero(datos, "presupuesto", 1000000000)
    capacidad_pedidos = numero(datos, "capacidadPedidos", 2000)
    minimo_comidas = numero(datos, "minimoComidas", 300)
    minimo_bebidas = numero(datos, "minimoBebidas", 400)
    relacion_bebidas_comidas = numero(datos, "relacionBebidasComidas", 0.60)
    productos_minimos_por_pedido = numero(
        datos,
        "productosMinimosPedido",
        numero(datos, "productosMaximosPedido", 1)
    )
    costo_domicilio = numero(datos, "costoDomicilio", 8000)

    ganancia_comida = numero(datos, "gananciaComida", 7150)
    ganancia_bebida = numero(datos, "gananciaBebida", 3240)
    ganancia_domicilio = numero(datos, "gananciaDomicilio", 2000)
    costo_comida = numero(datos, "costoComida", 57850)
    costo_bebida = numero(datos, "costoBebida", 14760)

    comidas_minimas_requeridas = minimo_comidas
    bebidas_minimas_requeridas = minimo_bebidas
    if relacion_bebidas_comidas > 0:
        for _ in range(12):
            bebidas_minimas_requeridas = max(bebidas_minimas_requeridas, relacion_bebidas_comidas * comidas_minimas_requeridas)
            comidas_minimas_requeridas = max(comidas_minimas_requeridas, relacion_bebidas_comidas * bebidas_minimas_requeridas)

    costo_minimo_productos = costo_comida * comidas_minimas_requeridas + costo_bebida * bebidas_minimas_requeridas
    if costo_minimo_productos > presupuesto:
        raise RuntimeError(
            "El costo de los productos excede el presupuesto disponible. "
            f"Presupuesto mínimo requerido: {pesos(costo_minimo_productos)}."
        )

    model = ConcreteModel()
    model.P = Var(domain=NonNegativeReals)
    model.C = Var(domain=NonNegativeReals)
    model.B = Var(domain=NonNegativeReals)
    model.D = Var(domain=NonNegativeReals)

    model.obj = Objective(
        expr=ganancia_comida * model.C + ganancia_bebida * model.B + ganancia_domicilio * model.D,
        sense=maximize,
    )

    model.presupuesto = Constraint(expr=costo_comida * model.C + costo_bebida * model.B + costo_domicilio * model.D <= presupuesto)
    model.capacidad_pedidos = Constraint(expr=model.P <= capacidad_pedidos)
    model.producto_minimo_por_pedido = Constraint(expr=model.C + model.B >= model.P)
    model.productos_minimos_por_pedido = Constraint(expr=model.C + model.B >= productos_minimos_por_pedido * model.P)
    model.domicilios_no_superan_pedidos = Constraint(expr=model.D <= model.P)
    model.minimo_comidas = Constraint(expr=model.C >= minimo_comidas)
    model.minimo_bebidas = Constraint(expr=model.B >= minimo_bebidas)
    model.relacion_bebidas_comidas = Constraint(expr=model.B >= relacion_bebidas_comidas * model.C)
    if relacion_bebidas_comidas > 0:
        model.balance_bebidas_comidas = Constraint(expr=model.B <= model.C / relacion_bebidas_comidas)

    solver = SolverFactory("glpk")
    if not solver.available(False):
        raise RuntimeError("GLPK no esta disponible. Instala glpk/glpsol para resolver el modelo.")

    result = solver.solve(model)
    estado = str(result.solver.status)
    terminacion = str(result.solver.termination_condition)
    if terminacion.lower() not in {"optimal", "feasible"}:
        raise RuntimeError(
            "El costo de los productos excede el presupuesto disponible. "
            f"Presupuesto mínimo requerido: {pesos(costo_minimo_productos)}."
        )

    pedidos = value(model.P)
    comidas = value(model.C)
    bebidas = value(model.B)
    domicilios = value(model.D)
    productos = comidas + bebidas
    ganancia = ganancia_comida * comidas + ganancia_bebida * bebidas + ganancia_domicilio * domicilios
    costo = costo_comida * comidas + costo_bebida * bebidas + costo_domicilio * domicilios
    presupuesto_sobrante = presupuesto - costo
    if abs(presupuesto_sobrante) < 0.01:
        presupuesto_sobrante = 0
    promedio_productos_pedido = productos / pedidos if pedidos else 0
    minimo_productos = productos_minimos_por_pedido * pedidos
    uso_minimo_productos = (productos / minimo_productos) * 100 if minimo_productos else 0
    restriccion_productos_activa = abs(productos - minimo_productos) < 0.01

    return {
        "estado": estado,
        "terminacion": terminacion,
        "pedidos": pedidos,
        "comidas": comidas,
        "bebidas": bebidas,
        "domicilios": domicilios,
        "productos": productos,
        "promedioProductosPedido": promedio_productos_pedido,
        "productosMinimosPedido": productos_minimos_por_pedido,
        "productosMaximosPedido": productos_minimos_por_pedido,
        "limiteProductos": minimo_productos,
        "usoLimiteProductos": uso_minimo_productos,
        "restriccionProductosActiva": restriccion_productos_activa,
        "ganancia": ganancia,
        "costo": costo,
        "presupuesto": presupuesto,
        "presupuestoSobrante": presupuesto_sobrante,
    }


try:
    entrada = json.load(sys.stdin)
    print(json.dumps(resolver(entrada), ensure_ascii=False))
except Exception as exc:
    print(str(exc), file=sys.stderr)
    sys.exit(1)
