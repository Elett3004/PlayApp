# PlayApp

## Ocupación con TensorFlow y FastAPI

La pantalla de clima consulta una red neuronal entrenada con un dataset simulado
de clima y calendario. Muestra el porcentaje del modelo y su procedencia; si no
está disponible, conserva las reglas de respaldo. Instalación, entrenamiento,
ejecución y límites: [ml/README.md](ml/README.md).

## Clima y ocupación estimada

Los administradores pueden entrar a `/manager/clima` desde el menú lateral.
Configura `OPENWEATHER_API_KEY` en `config/application-secrets.env` y reinicia la app.
Se usa Current Weather (`https://api.openweathermap.org/data/2.5/weather`), con
coordenadas de Bocagrande (10.399667, -75.555001), unidades métricas e idioma español.
Opcionalmente puedes ajustar `OPENWEATHER_LATITUDE` y `OPENWEATHER_LONGITUDE`.
La clave permanece en el servidor. Los datos se reutilizan durante 10 minutos;
hay tiempos de espera y pausa de reintentos cuando falla el proveedor. No se
presentan observaciones de más de una hora como clima actual.

La tabla ampliada de 15 condiciones está integrada. El Excel de referencia está en
[docs/Tabla_porcentajes_ocupacion.xlsx](docs/Tabla_porcentajes_ocupacion.xlsx).
Los porcentajes y textos se cargan de `src/main/resources/occupancy/rules.json`;
el cálculo usa esta fuente y la pantalla presenta un resumen escrito de la
ocupación con los datos meteorológicos actuales, sin exponer las reglas.
La vista usa un fondo de playa, una tarjeta de clima actual con seis indicadores,
un resumen breve de ocupación y un mapa Leaflet de Bocagrande. El análisis completo
y los datos secundarios se consultan en el modal «Ver análisis y más datos». El marcador usa
las mismas coordenadas configuradas para OpenWeatherMap. Los mapas base necesitan
conexión a OpenStreetMap; si fallan, se ofrece un enlace a la ubicación.

El fondo general usa una imagen de arena y olas. La tarjeta cambia su fotografía
según los códigos de clima recibidos, sensación térmica, viento y visibilidad:
sol, nubes parciales, cielo cubierto, calor, ambiente fresco, viento, lluvia ligera,
moderada o intensa, tormenta, bruma, niebla y noche. Hay imágenes para nieve,
partículas atmosféricas y tornado, y un respaldo para condiciones desconocidas.
Se priorizan fenómenos adversos para no mostrar sol cuando también se reporta lluvia.
El horario se determina con amanecer y atardecer; las imágenes nocturnas se oscurecen.
Son ilustraciones decorativas generadas con `image_gen`, no fotografías en vivo.
Los archivos están en `src/main/resources/static/img/weather/`; los prompts y
la procedencia están en [docs/weather-artwork-manifest.json](docs/weather-artwork-manifest.json).
Cambiar el Excel por sí solo no
actualiza la aplicación: mantener Excel, JSON y criterios del estimador sincronizados.

Se evalúan todos los códigos meteorológicos recibidos, sensación térmica, viento
y visibilidad. Si coinciden reglas, se elige el rango con menor mínimo y, en caso
de empate, menor máximo. No se suman ni promedian porcentajes ni se convierten a
cantidad de personas. La tabla distingue rangos originales y propuestas orientativas.

Correspondencias de OpenWeatherMap: 800 despejado, 801–802 parcialmente nublado,
803–804 nublado; 701/721/741 bruma o niebla según visibilidad (5 km es el límite).
Lluvia ligera/llovizna: 300/301/310/311/313/321/500/520; moderada:
501/521/531; intensa: 302/312/314/502/503/504/522 y tormentas del grupo 2xx.
La clasificación de llovizna fuerte y chubascos es una adaptación de la tabla.
Referencia: https://openweathermap.org/api/weather-conditions.

Durante la noche se estima 0–1 % de ocupación, como aproximación propuesta por el
usuario para representar afluencia muy baja con posible presencia de algunas personas.
Se determina la noche por amanecer y atardecer de la API, sin convertirla en una
afirmación sobre horarios oficiales de acceso. Se mantiene el control de datos válidos.
Sin visibilidad para bruma/niebla durante el día, sin datos actuales suficientes
o sin regla aplicable, se muestra «Sin estimación concluyente». El rango óptimo
80–100 % queda como referencia: requiere índice de calor y temperatura del mar,
que esta consulta no entrega. Los porcentajes nuevos requieren validación con
observaciones reales; no son mediciones de la ocupación de la playa.

Pruebas sin MongoDB ni Redis: `mvnw.cmd "-Dtest=BeachOccupancyEstimatorTest,CurrentWeatherServiceTest,WeatherTemplateTest" test`.

Proyecto Spring Boot 3 + Java 21 con MongoDB Atlas y Redis.

## Objetivo de esta sincronizacion local

Esta configuracion esta pensada para que **todo quede aislado por proyecto**:

- Docker con nombre de proyecto propio: `playapp-local`
- Volumenes Docker propios para desarrollo local: `playapp_mongo_data`, `playapp_redis_data`
- Variables de entorno solo en `config/application-secrets.env`
- VS Code configurado en `.vscode/` para este workspace
- Sin cambios en configuraciones globales de Git, Docker Desktop o VS Code

## Requisitos

- Java 21 (opcional si usas el instalador local `scripts/setup-java21.ps1`)
- Docker Desktop + Docker Compose, usado principalmente para Redis local
- Git (opcional para conectar a GitHub)

## Java 21 local (sin tocar tu Java global)

Instala JDK 21 portable dentro de este repo:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-java21.ps1
```

Se instala en `.tools/jdk-21` y se usa solo para PlayApp.

## 1) Configurar variables locales

Archivo base:

`config/application-secrets.env.example`

Copia a:

`config/application-secrets.env`

Variables importantes:

- `MONGODB_URI`
- `MONGODB_DATABASE`
- `REDIS_HOST`
- `REDIS_PORT`
- `MP_ACCESS_TOKEN`
- `GEMINI_API_KEY`
- `GEMINI_MODEL`
- `GEMINI_BASE_URL`
- `PLAYAPP_BASE_URL`
- `PLAYAPP_ADMIN_EMAIL`
- `PLAYAPP_ADMIN_PASSWORD`

Para usar MongoDB Atlas, configura:

```env
MONGODB_URI=mongodb+srv://usuario:password@cluster/playappdb?retryWrites=true&w=majority&appName=playapp-prod
MONGODB_DATABASE=playappdb
```

Puertos recomendados para no chocar con otros proyectos:

- Mongo local del proyecto: `27018`
- Redis local del proyecto: `6380`
- App local: `8080`

## 2) Levantar servicios locales para PlayApp

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\dev-up.ps1
```

Para detener:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\dev-down.ps1
```

## 3) Ejecutar la app local

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-playapp.ps1
```

El script:

- carga `config/application-secrets.env`
- si `MONGODB_URI` apunta a Atlas/remoto, levanta solo `redis` local
- si `MONGODB_URI` apunta a localhost, levanta `mongo` y `redis` locales
- si detecta Java 21, inicia Spring Boot local
- si NO detecta Java 21 (por ejemplo Java 17), cambia automaticamente a modo Docker (redis + app)

## 4) Ejecutar todo en Docker (incluyendo app)

```powershell
docker-compose --profile app up -d --build
```

Mongo local queda disponible solo para desarrollo puntual:

```powershell
docker-compose --profile local-mongo up -d mongo
```

## 5) VS Code (solo workspace)

Se incluyeron archivos en `.vscode/`:

- `settings.json`
- `tasks.json`
- `launch.json`
- `extensions.json`

No afectan otros proyectos.

## 6) GitHub (sin tocar global)

Si quieres conectar este repo local a GitHub:

```powershell
git remote add origin https://github.com/jtejadap/PlayApp.git
git branch -M main
```

Luego revisa antes de push:

```powershell
git status
```

## 7) Auto-deploy a Railway con GitHub Actions

Se agrego el workflow:

- `.github/workflows/railway-auto-deploy.yml`

Comportamiento:

- se ejecuta en cada `push` a `main`
- compila el proyecto (`mvn clean package -DskipTests`)
- despliega automaticamente a Railway con `railway up --ci`

Configura estos secretos en GitHub (`Settings > Secrets and variables > Actions`):

- `RAILWAY_TOKEN`: Project Token de Railway
- `RAILWAY_PROJECT_ID`: ID del proyecto
- `RAILWAY_ENVIRONMENT`: nombre o ID del entorno (ejemplo: `production`)
- `RAILWAY_SERVICE`: nombre o ID del servicio de la app

Notas de seguridad:

- no subas archivos de secretos al repo
- usa variables de entorno en Railway para `MONGODB_URI`, `MP_ACCESS_TOKEN`, `GEMINI_API_KEY`, etc.

## 8) Pasarela de Pago Wompi Sandbox y Flujo de Compra

PlayApp cuenta con una arquitectura de pagos desacoplada compatible con **Wompi Sandbox (Bancolombia)** para tarjetas de crédito/débito y opciones de pago contraentrega en playa (**Efectivo contraentrega** y **Transferencia contraentrega**).

### Configuración de Wompi Sandbox

En tu archivo `config/application-secrets.env` (o en variables de entorno del servidor), configura tus credenciales de prueba obtenidas en el portal de desarrolladores de Wompi:

```properties
WOMPI_PUBLIC_KEY=pub_test_TU_LLAVE_PUBLICA
WOMPI_PRIVATE_KEY=prv_test_TU_LLAVE_PRIVADA
WOMPI_INTEGRITY_SECRET=test_integrity_TU_SECRETO_INTEGRIDAD
WOMPI_EVENTS_SECRET=test_events_TU_SECRETO_EVENTOS
WOMPI_SANDBOX_URL=https://sandbox.wompi.co/v1
PLAYAPP_BASE_URL=http://localhost:8080
```

> [!IMPORTANT]
> - Nunca expongas la llave privada (`prv_test_...`) ni el secreto de integridad en el frontend ni en repositorios públicos.
> - La firma de integridad (`SHA-256`) se calcula exclusivamente en el backend de PlayApp antes de inicializar el widget de pago.

### Cómo Probar Pagos en Modo Sandbox

1. **Configurar Credenciales:** En `config/application-secrets.env`, ingresa tus credenciales de prueba obtenidas en el portal de comercios de Wompi (`https://comercios.wompi.co/`):
   ```properties
   WOMPI_PUBLIC_KEY=pub_test_TU_LLAVE_PUBLICA
   WOMPI_PRIVATE_KEY=prv_test_TU_LLAVE_PRIVADA
   WOMPI_INTEGRITY_SECRET=test_integrity_TU_SECRETO
   ```
2. **Iniciar la aplicación:** Ejecuta `.\scripts\start-playapp.ps1` o ejecuta el proyecto en VS Code.
3. **Flujo de Compra:**
   - Ve a la tienda (`/shop`) y agrega productos al carrito.
   - Ve al carrito (`/cart/checkout`), revisa las cantidades y pulsa **"Continuar a entrega"**.
   - En entrega (`/shipment/place`), define tu dirección en la playa de Cartagena, carpa/mesa opcional y pulsa **"Continuar al pago"**.
4. **Demostración de Pago Aprobado:**
   - Pulsa **"Pagar con tarjeta"** para abrir el formulario seguro oficial de Wompi.
   - **Número de tarjeta:** `4242 4242 4242 4242`
   - **Fecha de expiración:** Cualquier fecha futura (ej. `12/28`)
   - **CVC:** `123`
   - **Nombre del titular:** Cualquier nombre (ej. `Usuario Demo`)
   - Al procesarse, Wompi redirige a `/payment/wompi/redirect`, el backend valida la transacción con el API de Wompi y se muestra el mensaje de confirmación:
     > **"¡Pago aprobado! Tu pedido está siendo preparado."**
5. **Demostración de Transacción Rechazada:**
   - Repite el proceso e ingresa la tarjeta de rechazo de prueba:
     - **Número de tarjeta:** `4111 1111 1111 1111`
     - **Fecha de expiración:** Cualquier fecha futura
     - **CVC:** `123`
   - Al procesarse, se muestra un mensaje amable explicando que el pago fue rechazado y un botón destacado **"Intentar nuevamente"** para volver a la pasarela de pago.

### Guía de Desarrollo cuando Faltan Credenciales

Si las credenciales `WOMPI_PUBLIC_KEY`, `WOMPI_PRIVATE_KEY` o `WOMPI_INTEGRITY_SECRET` están vacías en `config/application-secrets.env`:
- La pantalla de pago **no simula un pago falso**.
- En su lugar, muestra una guía visible en amarillo con un checklist de las variables de entorno que faltan y el formato requerido (`pub_test_...` y `prv_test_...`) para orientar al desarrollador o evaluador.

## Seguridad

Se removieron secretos hardcodeados del codigo:

- credenciales de correo en `application.properties`
- token de MercadoPago reemplazado por arquitectura desacoplada Wompi
- firmas SHA-256 calculadas en backend

Ahora todo viene por variables locales del proyecto.
