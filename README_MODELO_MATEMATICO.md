# Modelo Matemático PlayApp

Este README resume el modelo actual usado por `src/main/resources/python/modelo_playapp.py` y deja un prompt listo para pedirle a ChatGPT una infografía como la imagen de referencia, pero actualizada con las restricciones y parámetros reales del sistema.

## Modelo actual

PlayApp resuelve un modelo de optimización con Pyomo y GLPK para encontrar la mejor combinación de pedidos, comidas, bebidas y domicilios, maximizando la ganancia total sin superar el presupuesto operativo.

### Variables de decisión

- `P`: número de pedidos al mes.
- `C`: cantidad de comidas vendidas al mes.
- `B`: cantidad de bebidas vendidas al mes.
- `D`: número de domicilios realizados al mes.

Todas las variables son enteras no negativas:

```text
P, C, B, D ∈ Z≥0
```

Esto significa que el modelo no recomienda valores fraccionarios como `2.5 comidas`; siempre devuelve unidades completas.

### Parámetros configurables

Estos valores se pueden cambiar desde la vista de administrador:

- `Presupuesto`: dinero operativo disponible.
- `Pmax`: capacidad máxima de pedidos.
- `Cmin`: mínimo de comidas.
- `Bmin`: mínimo de bebidas.
- `r`: relación mínima bebidas/comidas.
- `x`: mínimo promedio de productos por pedido.
- `cC`: costo operativo por comida.
- `cB`: costo operativo por bebida.
- `cD`: costo operativo por domicilio.
- `gC`: ganancia neta por comida.
- `gB`: ganancia neta por bebida.
- `gD`: ganancia neta por domicilio.

Los costos y ganancias pueden escribirse manualmente o calcularse desde promedios de precios:

- Comida: costo operativo 85%, ganancia 15%.
- Bebida: costo operativo 75%, ganancia 25%.
- Domicilio: costo operativo 90%, ganancia 10%.

### Función objetivo

El modelo maximiza la ganancia total:

```text
Max Z = gC*C + gB*B + gD*D
```

En el código se suma un valor técnico muy pequeño `0.000001*P` para desempatar soluciones con la misma ganancia y preferir más pedidos cuando no cambia el resultado económico.

### Restricciones

```text
cC*C + cB*B + cD*D ≤ Presupuesto
P ≤ Pmax
C + B ≥ P
C + B ≥ xP
D ≤ P
C ≥ Cmin
B ≥ Bmin
B ≥ rC
Si r > 0: B ≤ C/r
P, C, B, D ≥ 0 y enteros
```

Interpretación:

- El gasto operativo no puede superar el presupuesto.
- No se pueden atender más pedidos que la capacidad máxima.
- Cada pedido debe tener productos asociados.
- El promedio mínimo de productos por pedido se controla con `C + B ≥ xP`.
- Los domicilios no pueden superar los pedidos.
- Se respetan mínimos comerciales de comidas y bebidas.
- La relación `r` balancea bebidas frente a comidas. Si `r = 0`, no limita la relación. Si `r = 0.60`, las bebidas deben acompañar al menos el 60% de las comidas y también se evita un desbalance excesivo. Si `r = 1`, comidas y bebidas quedan balanceadas.

### Validación de presupuesto

Antes de resolver, el sistema calcula si el presupuesto alcanza para cubrir los mínimos obligatorios de comidas y bebidas. Si no alcanza, muestra un error como:

```text
El costo de los productos excede el presupuesto disponible. Presupuesto mínimo requerido: $XX.XXX.XXX.
```

El presupuesto puede ser cualquier valor, no tiene que ser múltiplo de 1.000. El sobrante se calcula como:

```text
Presupuesto sobrante = Presupuesto - costo operativo total
```

Como las variables son enteras, el sobrante representa dinero real que ya no alcanza para preparar otra unidad útil según las restricciones del modelo.

## Prompt para generar la infografía

Copia y pega este prompt en ChatGPT o en una herramienta de generación de imagen:

```text
Crea una infografía educativa, clara y bonita titulada "MODELO MATEMÁTICO PLAYAPP".

Estilo visual:
- Formato horizontal tipo póster académico.
- Fondo blanco o cuadrícula muy suave.
- Colores principales: azul PlayApp, verde, naranja y morado.
- Usa íconos modernos para pedidos, comidas, bebidas, domicilios, dinero, presupuesto y restricciones.
- Diseño organizado en bloques numerados, con bordes redondeados y jerarquía clara.
- Debe verse profesional, limpio y fácil de leer para una presentación universitaria.

Tema:
Modelo de optimización de PlayApp para maximizar ventas, eficiencia de domicilios y ganancias usando Pyomo + GLPK.

Bloque 1: Variables de decisión
Explica:
P = número de pedidos al mes.
C = cantidad de comidas vendidas al mes.
B = cantidad de bebidas vendidas al mes.
D = número de domicilios realizados al mes.
Indica que todas son variables enteras no negativas:
P, C, B, D ∈ Z≥0.

Bloque 2: Función objetivo
Mostrar la fórmula general, sin valores fijos:
Max Z = gC*C + gB*B + gD*D
Donde:
gC = ganancia neta por comida.
gB = ganancia neta por bebida.
gD = ganancia neta por domicilio.
Aclara que estos valores son configurables desde la pantalla Costo/Beneficio.
Menciona de forma pequeña que el sistema usa un desempate técnico mínimo para preferir más pedidos cuando la ganancia es igual.

Bloque 3: Costos y beneficios configurables
Tabla con columnas:
Concepto | Costo operativo por unidad | Ganancia neta por unidad | Cálculo sugerido
Filas:
Comidas (C) | cC | gC | costo 85% del precio promedio, ganancia 15%
Bebidas (B) | cB | gB | costo 75% del precio promedio, ganancia 25%
Domicilios (D) | cD | gD | costo 90% del precio promedio, ganancia 10%
Explica que el usuario puede escribir los valores manualmente o cargarlos desde precios promedio.

Bloque 4: Restricciones del modelo
Mostrar las restricciones:
1. cC*C + cB*B + cD*D ≤ Presupuesto
2. P ≤ Pmax
3. C + B ≥ P
4. C + B ≥ xP
5. D ≤ P
6. C ≥ Cmin
7. B ≥ Bmin
8. B ≥ rC
9. Si r > 0: B ≤ C/r
10. P, C, B, D ∈ Z≥0

Al lado de cada restricción agrega una explicación corta:
1. No se puede gastar más del presupuesto disponible.
2. PlayApp no supera su capacidad máxima de pedidos.
3. Cada pedido debe tener al menos productos asociados.
4. El parámetro x define el mínimo promedio de productos por pedido.
5. No puede haber más domicilios que pedidos.
6. Se asegura un mínimo de comidas.
7. Se asegura un mínimo de bebidas.
8. La relación r exige bebidas mínimas frente a comidas.
9. Si r es mayor que cero, también balancea para evitar exceso de bebidas frente a comidas.
10. No hay cantidades negativas ni fraccionarias.

Bloque 5: Flujo de funcionamiento
Ilustra con flechas:
El administrador configura presupuesto, capacidad, mínimos, relación bebidas/comidas, promedio mínimo por pedido y costos/beneficios.
Luego presiona "Ejecutar Modelo".
El backend llama Python.
Pyomo construye el modelo.
GLPK resuelve.
La vista muestra pedidos, comidas, bebidas, domicilios, ganancia, costo y presupuesto sobrante.

Bloque 6: Interpretación de resultados
Incluye estas salidas:
Pedidos óptimos (P)
Comidas óptimas (C)
Bebidas óptimas (B)
Domicilios óptimos (D)
Ganancia máxima (Z)
Costo operativo total
Presupuesto sobrante
Productos vendidos totales (C+B)
Promedio productos por pedido

Bloque 7: Nota importante
Texto:
"El modelo busca la mejor combinación entera de productos y pedidos para maximizar la ganancia, respetando presupuesto, capacidad operativa, mínimos comerciales y balance entre comidas y bebidas. El presupuesto sobrante representa dinero que no alcanza para producir otra unidad útil según las restricciones actuales."

No uses los valores antiguos fijos 7150, 3240, 2000, 57850, 14760 ni 8000 como si fueran obligatorios. Esos valores ahora son configurables.
```
