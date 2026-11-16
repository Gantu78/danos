# DanosCampo — Evaluación de daños en sitios de desastre

App móvil que registra la **posición (lat/lon)** y el **nivel de afectación (1 a 5)** de sitios de desastre,
**funciona sin internet** y **sincroniza** con el sistema central al volver la red.

| Entregable | Dónde |
| --- | --- |
| Diseño (PDF) | [docs/DISENO.pdf](docs/DISENO.pdf) |
| Diseño (LaTeX para Overleaf) | [docs/latex/](docs/latex/): `main.tex`, diagramas Mermaid y `DanosCampo-diseno-overleaf.zip` |
| Diseño (resumen en Markdown) | [docs/DISENO.md](docs/DISENO.md) |
| Código de la app Android | [app/](app/) (Kotlin, Jetpack Compose, `applicationId = co.plataforma.danos`, v2.0.0) |
| Código del sistema central (prototipo inicial) | [api/](api/) (ASP.NET Core .NET 10 + PostgreSQL) |
| Ejemplo de configuración de firma | [keystore.properties.ejemplo](keystore.properties.ejemplo) |

## 1. Inicio de sesión OIDC y modo de transición

### Inicio de sesión OIDC (AppAuth + Keycloak)

La app se autentica mediante **OpenID Connect (Authorization Code + PKCE S256)** con la biblioteca
`net.openid:appauth` contra el realm `plataforma` de Keycloak:

- **Cliente OIDC:** `movil` (público, sin secreto en el APK).
- **Scopes:** `openid profile offline_access`.
- **URI de redirección:** `co.plataforma.danos:/oauth2redirect`.
- **Emisor por defecto:** `http://10.0.2.2:8180/realms/plataforma` (configurable en la pantalla **Ingreso**; se descubre mediante `/.well-known/openid-configuration`).
- **Gateway por defecto en `debug`:** `http://10.0.2.2:8080`. En `release` el valor inicial es `https://` y exige configurar un servidor HTTPS.
- **Identidad desde el token:** el evaluador (`preferred_username`) y la organización (claim `organizacion`) se extraen del JWT y llenan `EvaluacionDto`. En modo OIDC el cliente HTTP envía `Authorization: Bearer <access token>` y `X-Dispositivo`, y **no** envía `X-Usuario` ni `X-Organizacion`.
- **Almacenamiento cifrado y renovación:** el estado OIDC (`AuthState`) se cifra con una llave AES-256-GCM del **Android Keystore** antes de guardarse en DataStore y nunca se escribe en texto plano ni en logs. Antes de cada sincronización (en segundo plano con `SincronizacionWorker` o en primer plano) se ejecuta `AuthState.performActionWithFreshTokens`.
- **Supervivencia de la cola local (AD-44):** si el `refresh_token` vence o es revocado, la cola de operaciones pendientes **no se pierde ni se bloquea**: la app muestra *"Inicie sesión para sincronizar"*, el worker devuelve `retry` sin alterar la cola y, tras iniciar sesión de nuevo, sincroniza las evaluaciones pendientes. Cerrar sesión borra únicamente los tokens locales y conserva las evaluaciones pendientes.

### Modo de transición (solo en `debug`)

Mientras se prueba contra `danos/api` (puerto `5080`) o contra un servicio sin gateway que aún lee encabezados:

1. En la compilación `debug`, la pantalla **Ingreso** incluye el interruptor **"Identidad por encabezados (desarrollo)"**.
2. Al activarlo, aparecen los campos manuales de **Evaluador** y **Organización** y la app envía `X-Usuario`, `X-Organizacion` y `X-Dispositivo`.
3. En la variante `release` este modo **no existe** (ni en la interfaz ni en el interceptor): solo se permite OIDC sobre HTTPS.

## 2. Descarga incremental y filtro por zona

La pantalla **Descarga** consume `GET /v1/descarga?desastreId={guid}&zona={divipola}&desde={instanteServidor}`:

- **Filtro por desastre y municipio (`zona`):** al elegir un desastre que informa `codigosMunicipio`, aparece el selector opcional de municipio (código DIVIPOLA) para descargar únicamente los activos de esa zona.
- **Descarga incremental (`desde`):** la app guarda el `instanteServidor` de la última descarga exitosa y lo envía como `desde` en la siguiente.
  - Si el servidor responde `incremental = true`, la app **fusiona** los activos localmente por `id` y `version` (inserta nuevos, actualiza existentes cuando la versión entrante es mayor o igual, no toca activos creados en campo ni con operaciones pendientes y **nunca borra** los activos que no vinieron en el lote incremental).
  - Si responde `incremental = false`, aplica una descarga completa. Los desastres que dejan de llegar en una descarga completa se retiran de la base local **solo si no tienen evaluaciones pendientes de sincronizar**.
- **Descarga completa:** el botón **"Descarga completa"** borra el marcador `desde` y fuerza la descarga íntegra del catálogo y activos.
- **Municipio del activo:** cuando un activo incluye `codigoMunicipio`, se muestra junto a su referencia (`Referencia (DIVIPOLA)`).
- **Migración de base de datos local:** Room migra de la versión 1 a la 2 mediante `MIGRACION_1_2` explícita (esquemas exportados en `app/schemas/`), conservando todas las evaluaciones y operaciones pendientes.

## 3. Compilar, probar y firmar la variante `release`

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
# Todas las pruebas instrumentadas (incluye migración Room 1 -> 2 con MigrationTestHelper)
./gradlew connectedDebugAndroidTest
```

La prueba `ContratoServidorTest` acepta parámetros por `AndroidJUnitRunner` (`urlApi`, `urlEmisor`, `modoIdentidad`):

```bash
# Contra el gateway (:8080) y Keycloak (:8180) usando token de prueba (evaluador.armenia / evaluador.armenia_local)
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.danos.infraestructura.sync.ContratoServidorTest \
  -Pandroid.testInstrumentationRunnerArguments.urlApi=http://10.0.2.2:8080 \
  -Pandroid.testInstrumentationRunnerArguments.urlEmisor=http://10.0.2.2:8180/realms/plataforma \
  -Pandroid.testInstrumentationRunnerArguments.modoIdentidad=oidc

# En modo encabezados contra el servidor de desarrollo (:5080)
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.danos.infraestructura.sync.ContratoServidorTest \
  -Pandroid.testInstrumentationRunnerArguments.urlApi=http://10.0.2.2:5080 \
  -Pandroid.testInstrumentationRunnerArguments.modoIdentidad=encabezados
```

Si el servidor no responde, `ContratoServidorTest` se omite automáticamente con `assumeTrue`.

## 4. Estructura

```
├── app/                         App Android (presentacion · acceso · dominio · infraestructura)
├── api/                         API .NET del prototipo inicial y simulador de QA-14
├── docs/DISENO.md               Arquitectura, modelo de datos, sincronización y mapa de calor
├── keystore.properties.ejemplo  Plantilla para firmar la variante release
└── docker-compose.yml           API + PostgreSQL del prototipo inicial
```
