# Microservicio de Evaluación de Daños

API en ASP.NET Core (.NET 10) + EF Core + PostgreSQL 18 que recibe por lotes las evaluaciones que la app
móvil registró sin conexión. Detecta conflictos (regla determinista por versión del activo) y posibles
duplicados (Haversine ≤ 25 m, ±24 h) sin resolverlos nunca: conserva ambas versiones para revisión humana.
Cumple QA-14: 120 evaluaciones con 5 conflictos → 0 pérdidas, 0 sobrescrituras, 100 % detectados, reenvío sin
duplicados, en segundos. También sirve el mapa de calor de lo ya sincronizado en `/mapa`.

**Datos iniciales.** Al arrancar en Development el API crea la base, la migra y siembra solo lo que la app
necesita para trabajar: los 11 tipos de activo con su criterio por cada nivel de daño y 3 desastres (GUID fijos
`d0000000-0000-0000-0000-00000000000{1,2,3}`). No siembra activos ni evaluaciones: los activos los registra la
app en campo (`activoNuevo` con `versionBaseActivo: null`) y todo lo demás llega por sincronización. La primera
descarga devuelve `activos: []`.

## Estructura

```
api/
├── Danos.sln
├── pruebas.http                     un ejemplo de cada endpoint
├── datos-ejemplo/foto.jpg           foto de 1×1 px que usa pruebas.http
├── src/
│   ├── Danos.Api/
│   │   ├── Acceso/                  controllers, DTOs, FiltroEncabezados
│   │   ├── Dominio/                 entidades, enums, ReglaConflicto, DetectorDuplicados,
│   │   │                            ValidadorEvaluacion, ServicioSincronizacion, ServicioDescarga,
│   │   │                            ServicioEvidencias, ServicioRevision, ServicioMapaCalor
│   │   ├── Infraestructura/         DanosDbContext, Fluent API, Semilla, AlmacenArchivos, Haversine,
│   │   │                            Opciones, Migraciones/ (Inicial)
│   │   ├── Pages/                   Mapa.cshtml (mapa de calor, Razor Pages)
│   │   ├── wwwroot/                 css/sitio.css, js/calor.js (Leaflet 1.9.4 + Leaflet.heat 0.2.0)
│   │   ├── appsettings.json         Kestrel 0.0.0.0:5080, Evidencias, Duplicados, Sincronizacion
│   │   └── appsettings.Development.json   ← cadena de conexión (cambiar la contraseña aquí)
│   └── Danos.Simulador/             simulador de QA-14
└── tests/Danos.Api.Tests/           unitarias + integración (WebApplicationFactory)
```

## Comandos

Todos desde la carpeta `api/`. La cadena de conexión está en
`src/Danos.Api/appsettings.Development.json` → `ConnectionStrings:Danos`, con `Password=CAMBIAR`.
Para no subir la contraseña al repositorio, ponla en los user-secrets del proyecto (tienen prioridad sobre
el archivo y también los leen las pruebas):

```bash
dotnet user-secrets set "ConnectionStrings:Danos" "Host=localhost;Port=5432;Database=evaluacion_danos;Username=postgres;Password=TU_CONTRASENA" --project src/Danos.Api
```

También sirve la variable de entorno `ConnectionStrings__Danos`, o reemplazar `CAMBIAR` en el archivo.

Crear la base (opcional: al arrancar en Development el API la crea, la migra y siembra catálogo y desastres):

```bash
psql -h localhost -U postgres -c "CREATE DATABASE evaluacion_danos"
```

Herramienta de migraciones (una vez):

```bash
dotnet tool install --global dotnet-ef
```

Migración inicial (ya está generada en `Infraestructura/Migraciones`; solo para regenerarla):

```bash
dotnet ef migrations add Inicial --project src/Danos.Api --output-dir Infraestructura/Migraciones
```

Aplicar migraciones sin arrancar el API:

```bash
dotnet ef database update --project src/Danos.Api
```

Correr el API (http://0.0.0.0:5080; desde el emulador Android es http://10.0.2.2:5080):

```bash
dotnet run --project src/Danos.Api --launch-profile http
```

Pruebas (crean y borran `evaluacion_danos_test` en cada corrida; sus 300 activos de prueba solo existen ahí):

```bash
dotnet test
```

Simulador de QA-14. Crea sus propios 120 activos y escribe datos simulados, así que conviene correrlo contra
una base aparte para no mezclarlos con los de la app. En una terminal, el API apuntando a `evaluacion_danos_sim`
(la variable de entorno tiene prioridad sobre user-secrets y el API crea esa base sola):

```bash
$env:ConnectionStrings__Danos = "Host=localhost;Port=5432;Database=evaluacion_danos_sim;Username=postgres;Password=TU_CONTRASENA"
```

```bash
dotnet run --project src/Danos.Api --launch-profile http
```

Y en otra terminal:

```bash
dotnet run --project src/Danos.Simulador -- --url http://localhost:5080
```

Mapa de calor: abrir http://localhost:5080/mapa · OpenAPI: http://localhost:5080/openapi/v1.json

## Conectar la app Android

- URL base: `http://10.0.2.2:5080` desde el emulador; `http://<IP de este PC en la LAN>:5080` desde un celular
  en la misma Wi-Fi (Windows pide permitir el acceso la primera vez; si no, crear una regla de entrada para el
  puerto TCP 5080 en el Firewall de Windows).
- Es HTTP sin TLS: la app necesita permitir tráfico en claro (`android:usesCleartextTraffic="true"` o un
  `network_security_config` para esa IP).
- Encabezados en cada llamada a `/v1/*`: `X-Usuario`, `X-Organizacion`, `X-Dispositivo`; el `idDispositivo` del
  lote debe ser igual a `X-Dispositivo`. OkHttp no deja enviar tildes en un encabezado: codificar el nombre del
  evaluador o quitarle las tildes.
- Flujo esperado: `GET /v1/descarga` → registrar sin red (activo nuevo con `activoNuevo`, o uno descargado con
  su `version` como `versionBaseActivo`) → `POST /v1/sincronizacion/evaluaciones` en lotes de hasta 100 →
  `PUT /v1/evidencias/{id}` por cada foto declarada.

## Endpoints

| Método y ruta | Qué hace |
| --- | --- |
| `GET /v1/descarga?desastreId=` | Tipos de activo con criterios, desastres y activos registrados (con su versión) |
| `POST /v1/sincronizacion/evaluaciones` | Lote de hasta 100 operaciones; un resultado por operación, en orden |
| `PUT /v1/evidencias/{id}` | Foto (multipart, parte `archivo`); 404 no declarada, 413 > 10 MB, 422 hash distinto |
| `GET /v1/conflictos?estado=PENDIENTE` | Conflictos con la versión vigente y la entrante completas |
| `GET /v1/evaluaciones?idDispositivo=` | Evaluaciones de un dispositivo con estado y nº de versiones |
| `GET /v1/mapa-calor?desastreId=&nivelMinimo=` | Puntos de la evaluación vigente de cada activo, con peso por nivel |
| `GET /mapa?desastreId=` | Página con el mapa de calor (refresco cada 10 s) |
| `GET /salud` | 200 |

Todo `/v1/*` exige `X-Usuario`, `X-Organizacion` y `X-Dispositivo` (400 si falta alguno). Se aceptan en UTF-8.

## Versiones y supuestos

- Paquetes: Npgsql.EntityFrameworkCore.PostgreSQL 10.0.3, Microsoft.EntityFrameworkCore.Design/Relational 10.0.14, Microsoft.AspNetCore.OpenApi 10.0.14; pruebas con xunit 2.9.3, xunit.runner.visualstudio 4.0.1, Microsoft.NET.Test.Sdk 18.11.0, Microsoft.AspNetCore.Mvc.Testing 10.0.14.
- Un atributo faltante, un texto más largo que su columna, un instante fuera de 1900–2200, un valor fuera de catálogo o una `versionBaseActivo` que el servidor nunca emitió dan INVALIDA en esa operación (no 400 al lote), con `estadoEvaluacion: RECHAZADA`; el reenvío devuelve lo mismo.
- Si una operación falla por un error inesperado aun después de reintentar, el lote se corta ahí y responde 503: lo anterior ya hizo commit, la app reenvía y se retoma en orden (0 pérdidas, nada queda INVALIDA por error).
- Editar una evaluación con conflicto pendiente la aplica pero la deja EN_CONFLICTO; una ACTUALIZAR no puede cambiar el activo; en un DUPLICADO, `Conflicto.ActivoId` es el activo nuevo. La creación de activos nuevos se serializa por tipo (candado de PostgreSQL) para no perder duplicados entre lotes simultáneos. El `idDispositivo` del cuerpo debe coincidir con `X-Dispositivo`.
- No se siembran los 300 activos que pedía el prompt: la base queda lista para los datos reales de la app (decisión del equipo); las pruebas y el simulador crean los suyos.
- Simplificaciones declaradas: sin TLS ni API Gateway (identidad por encabezados) y mapa de calor servido por este API leyendo su propia base (en el diseño completo es del Servicio de Consulta, AD-34).
