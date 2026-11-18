# DanosCampo — Evaluación de daños en sitios de desastre

App móvil de campo de la Plataforma para la Gestión de Desastres y Reconstrucción. **Registra y caracteriza un
desastre** desde el sitio, registra la **posición (lat/lon)** y el **nivel de afectación (1 a 5)** de los activos
dañados, **funciona sin internet** y **sincroniza** con la plataforma a través del gateway al volver la red.

> **Versión 2.1.0** (`versionCode 3`). APK de entrega: [entrega/DanosCampo.apk](entrega/DanosCampo.apk), variante
> `debug` (admite `http://` para el emulador y un computador en la misma red). Verificada contra el gateway y Keycloak
> de `proyectoparte2` el 18 de noviembre de 2026: 81 pruebas unitarias y 16 instrumentadas en verde (sección 3).
>
> `docs/DISENO.md`, `docs/DISENO.pdf` y `docs/latex/` describen la primera versión (contra `danos/api` en el puerto
> 5080, sin OIDC). La arquitectura vigente es la del SAD de la plataforma.

| Entregable | Dónde |
| --- | --- |
| Diseño (PDF) | [docs/DISENO.pdf](docs/DISENO.pdf) |
| Diseño (LaTeX para Overleaf) | [docs/latex/](docs/latex/): `main.tex`, diagramas Mermaid y `DanosCampo-diseno-overleaf.zip` |
| Diseño (resumen en Markdown) | [docs/DISENO.md](docs/DISENO.md) |
| Código de la app Android | [app/](app/) (Kotlin, Jetpack Compose, `applicationId = co.plataforma.danos`, v2.1.0) |
| APK de entrega | [entrega/DanosCampo.apk](entrega/DanosCampo.apk) (variante `debug`, v2.1.0) |
| Código del sistema central (prototipo inicial, reemplazado) | [api/](api/) (ASP.NET Core .NET 10 + PostgreSQL). La versión vigente es `proyectoparte2/servicios/evaluacion-danos`, detrás del gateway |
| Ejemplo de configuración de firma | [keystore.properties.ejemplo](keystore.properties.ejemplo) |

## 1. Inicio de sesión OIDC y modo de transición

### Inicio de sesión OIDC (AppAuth + Keycloak)

La app se autentica mediante **OpenID Connect (Authorization Code + PKCE S256)** con la biblioteca
`net.openid:appauth` contra el realm `plataforma` de Keycloak:

- **Cliente OIDC:** `movil` (público, sin secreto en el APK).
- **Scopes:** `openid profile offline_access`.
- **URI de redirección:** `co.plataforma.danos:/oauth2redirect`.
- **Emisor por defecto:** `http://localhost:8180/realms/plataforma` (configurable en la pantalla **Ingreso**; se descubre mediante `/.well-known/openid-configuration`).
  - **En el emulador de Android:** ejecute antes `adb reverse tcp:8180 tcp:8180` en el computador para reenviar el puerto `8180` del emulador al host. Así la app conversa con `http://localhost:8180/realms/plataforma` y el claim `iss` del JWT coincide exactamente con el emisor que validan el gateway y los microservicios (DA-15).
- **Gateway por defecto en `debug`:** `http://10.0.2.2:8080`. En `release` el valor inicial es `https://` y exige configurar un servidor HTTPS.
- **Identidad desde el token:** el evaluador (`preferred_username`) y la organización (claim `organizacion`) se extraen del JWT y llenan `EvaluacionDto`. En modo OIDC el cliente HTTP envía `Authorization: Bearer <access token>` y `X-Dispositivo`, y **no** envía `X-Usuario` ni `X-Organizacion`.
- **Almacenamiento cifrado y renovación:** el estado OIDC (`AuthState`) se cifra con una llave AES-256-GCM del **Android Keystore** antes de guardarse en DataStore y nunca se escribe en texto plano ni en logs. Antes de cada sincronización (en segundo plano con `SincronizacionWorker` o en primer plano) se ejecuta `AuthState.performActionWithFreshTokens`.
- **Supervivencia de la cola local (AD-44):** si el `refresh_token` vence o es revocado, la cola de operaciones pendientes **no se pierde ni se bloquea**: la app muestra *"Inicie sesión para sincronizar"*, el worker devuelve `retry` sin alterar la cola y, tras iniciar sesión de nuevo, sincroniza las evaluaciones pendientes. Cerrar sesión borra únicamente los tokens locales y conserva las evaluaciones pendientes.

### Modo de transición (solo en `debug`)

Mientras se prueba contra `danos/api` (puerto `5080`) o contra un servicio sin gateway que aún lee encabezados:

1. En la compilación `debug`, la pantalla **Ingreso** incluye el interruptor **"Identidad por encabezados (desarrollo)"**.
2. Al activarlo, aparecen los campos manuales de **Evaluador** y **Organización** y la app envía `X-Usuario`, `X-Organizacion` y `X-Dispositivo`.
3. En la variante `release` este modo **no existe** (ni en la interfaz ni en el interceptor): solo se permite OIDC sobre HTTPS.

## 2. Registro y caracterización de un desastre en campo

El ícono de alerta de la lista abre **Desastres registrados en campo**; el botón **Registrar desastre** abre el
formulario (RF-GD-02, RF-GD-03, RF-GD-05 y RF-GD-08 del SRS):

- **Caracterización:** tipo de evento (catálogo vigente del servidor, `GET /v1/catalogos/tipos-desastre`; sin red usa
  los tipos de RN-1), nombre, descripción, nivel RN-3 (1 a 4), hora de inicio y organismo responsable.
- **Zona afectada:** la ubicación GPS y un polígono de 100 m, 500 m, 1 km o 5 km alrededor del punto (fuente
  `EVALUACION_CAMPO`), y los municipios DIVIPOLA que se conozcan.
- **Sin conexión:** el registro se guarda en Room (tabla `registro_desastre`, versión 3 de la base, migración 2 a 3
  aditiva) y lo envía el mismo `SincronizacionWorker`, antes que las evaluaciones (AD-44).
- **Reenvío idempotente:** el teléfono genera el `id` del desastre y lo manda en `POST /v1/desastres`; si la respuesta
  se pierde y se reenvía, el servidor devuelve el mismo desastre.
- **Permiso:** registrar exige el rol `organismo` o `autoridad` (usuario de prueba `bomberos.operador`). Con otro rol el
  formulario lo advierte y el registro queda **Rechazado** con el motivo al sincronizar. Sin red, 5xx o sesión vencida
  el registro sigue **Pendiente** y se reintenta.

## 3. Descarga incremental y filtro por zona

La pantalla **Descarga** consume `GET /v1/descarga?desastreId={guid}&zona={divipola}&desde={instanteServidor}`:

- **Filtro por desastre y municipio (`zona`):** al elegir un desastre que informa `codigosMunicipio`, aparece el selector opcional de municipio (código DIVIPOLA) para descargar únicamente los activos de esa zona.
- **Descarga incremental (`desde`):** la app guarda el `instanteServidor` de la última descarga exitosa y lo envía como `desde` en la siguiente.
  - Si el servidor responde `incremental = true`, la app **fusiona** los activos localmente por `id` y `version` (inserta nuevos, actualiza existentes cuando la versión entrante es mayor o igual, no toca activos creados en campo ni con operaciones pendientes y **nunca borra** los activos que no vinieron en el lote incremental).
  - Si responde `incremental = false`, aplica una descarga completa. Los desastres que dejan de llegar en una descarga completa se retiran de la base local **solo si no tienen evaluaciones pendientes de sincronizar**.
- **Descarga completa:** el botón **"Descarga completa"** borra el marcador `desde` y fuerza la descarga íntegra del catálogo y activos.
- **Municipio del activo:** cuando un activo incluye `codigoMunicipio`, se muestra junto a su referencia (`Referencia (DIVIPOLA)`).
- **Migración de base de datos local:** Room migra de la versión 1 a la 2 (`MIGRACION_1_2`) y de la 2 a la 3 (`MIGRACION_2_3`, registro de desastres), con los esquemas exportados en `app/schemas/`, conservando todas las evaluaciones y operaciones pendientes.

## 4. Compilar, probar y firmar la variante `release`

### Compilación y pruebas unitarias (JVM)

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

### Cómo firmar la build `release`

La variante `release` tiene `isMinifyEnabled = true` (R8/ProGuard) y exige TLS (`cleartextTrafficPermitted="false"`).

1. Copie `keystore.properties.ejemplo` a `keystore.properties` en la raíz de `danos` (este archivo está en `.gitignore` y nunca se sube al control de versiones):
   ```properties
   storeFile=danos-release.jks
   storePassword=tu_clave_almacen
   keyAlias=danos
   keyPassword=tu_clave_llave
   ```
2. Genere su almacén de llaves si aún no tiene uno:
   ```bash
   keytool -genkeypair -v -keystore danos-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias danos
   ```
3. Ejecute `./gradlew assembleRelease`. Si `keystore.properties` existe, genera el APK firmado en `app/build/outputs/apk/release/app-release.apk`; si **no** existe (por ejemplo en CI), `assembleRelease` compila igualmente sin fallar (`app-release-unsigned.apk`).

### Pruebas instrumentadas (Room y Contrato)

Con un emulador o dispositivo conectado:

```bash
# Todas las pruebas instrumentadas (incluye las migraciones Room 1 -> 2 y 2 -> 3 con MigrationTestHelper)
./gradlew connectedDebugAndroidTest
```

La prueba `ContratoServidorTest` acepta parámetros por `AndroidJUnitRunner` (`urlApi`, `urlEmisor`, `modoIdentidad`).
Para ejecutarla en modo OIDC desde el emulador contra Keycloak (`:8180`) y los servicios en compose (`IDENTIDAD_MODO=Jwt`), habilite primero el túnel de puerto con `adb reverse`:

```bash
# 1. Reenviar el puerto 8180 del emulador al Keycloak del host para que el emisor (iss) coincida
adb reverse tcp:8180 tcp:8180

# 2. Contra el gateway (:8080) y Keycloak (http://localhost:8180/realms/plataforma) usando token de prueba (evaluador.armenia / evaluador.armenia_local)
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.danos.infraestructura.sync.ContratoServidorTest \
  -Pandroid.testInstrumentationRunnerArguments.urlApi=http://10.0.2.2:8080 \
  -Pandroid.testInstrumentationRunnerArguments.urlEmisor=http://localhost:8180/realms/plataforma \
  -Pandroid.testInstrumentationRunnerArguments.modoIdentidad=oidc

# En modo encabezados contra el servidor de desarrollo (:5080)
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.danos.infraestructura.sync.ContratoServidorTest \
  -Pandroid.testInstrumentationRunnerArguments.urlApi=http://10.0.2.2:5080 \
  -Pandroid.testInstrumentationRunnerArguments.modoIdentidad=encabezados
```

Si el servidor no responde, `ContratoServidorTest` se omite automáticamente con `assumeTrue`.

En modo OIDC, `ContratoServidorTest` también registra un desastre como `bomberos.operador` (y verifica que el reenvío
no lo duplica) y comprueba que `evaluador.armenia` recibe el rechazo por rol.

**Puerto ocupado en Windows.** Si otro programa ocupa el 8080 del computador, levante el gateway en otro puerto
(`PUERTO_WEB=18080 PUERTO_WEB_REPLICA=18081 docker compose ... up -d`) y pase `urlApi=http://10.0.2.2:18080`.
Compruebe antes que el puerto esté libre en `127.0.0.1` y no solo en `0.0.0.0`: el emulador llega por `127.0.0.1`.

## 5. Estructura

```
├── app/                         App Android (presentacion · acceso · dominio · infraestructura)
├── api/                         API .NET del prototipo inicial (reemplazado) y simulador de QA-14
├── docs/DISENO.md               Arquitectura, modelo de datos, sincronización y mapa de calor
├── keystore.properties.ejemplo  Plantilla para firmar la variante release
└── docker-compose.yml           API + PostgreSQL del prototipo inicial
```
