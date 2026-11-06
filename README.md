# DanosCampo — Evaluación de daños en sitios de desastre

App móvil que registra la **posición (lat/lon)** y el **nivel de afectación (1 a 5)** de sitios de desastre,
**funciona sin internet** y **sincroniza** con el sistema central al volver la red. El sistema central muestra un
**mapa de calor** por desastre.

| Entregable | Dónde |
| --- | --- |
| Diseño | [docs/DISENO.md](docs/DISENO.md) |
| Código de la app Android | [app/](app/) (Kotlin, Jetpack Compose) |
| Código del sistema central | [api/](api/) (ASP.NET Core .NET 10 + PostgreSQL) |
| APK para pruebas | [entrega/DanosCampo.apk](entrega/DanosCampo.apk) |

## 1. Levantar el sistema central

Requiere [Docker Desktop](https://www.docker.com/products/docker-desktop/). Desde la raíz del repositorio:

```bash
docker compose up -d --build
```

La primera vez tarda unos minutos (descarga las imágenes y compila el API). Al arrancar crea la base y siembra
11 tipos de activo y 3 desastres. Para comprobarlo:

- Salud: http://localhost:5080/salud
- Mapa de calor: http://localhost:5080/mapa

Para detenerlo: `docker compose down`. Para borrar también los datos: `docker compose down -v`.

## 2. Instalar la app

- **Emulador:** arrastra `entrega/DanosCampo.apk` sobre la ventana del emulador o ejecuta `adb install entrega/DanosCampo.apk`.
- **Celular Android 8.0 o superior:** copia el APK al teléfono, ábrelo y permite instalar desde esa fuente.

En la pantalla **Ingreso**, la URL del API depende de dónde corre la app:

| Dónde corre la app | URL del API |
| --- | --- |
| Emulador de Android Studio | `http://10.0.2.2:5080` (ya viene por defecto) |
| Celular en la misma Wi-Fi que el PC | `http://<IP del PC>:5080`, por ejemplo `http://192.168.1.10:5080` |

En Windows la IP del PC sale con `ipconfig` (Dirección IPv4). Si el celular no conecta, permite el puerto TCP 5080 en
el Firewall de Windows.

## 3. Guion de prueba

1. **Ingreso:** escribe evaluador y organización → Guardar.
2. **Descarga:** ícono de nube con flecha → Descargar. Debe mostrar 11 tipos de activo.
3. **Nueva evaluación:** elige **Activo nuevo** y escribe una referencia. Acepta el permiso de ubicación (aparecen
   las coordenadas y la precisión). Elige tipo de activo y nivel **1 a 5** (se muestra su criterio), **elige un
   desastre**, escribe la descripción y, si quieres, toma una foto → Guardar.
4. **Sin internet:** activa el modo avión y guarda otra evaluación. Queda **Pendiente** y el contador de la barra sube.
5. **Sincronización:** quita el modo avión. Sin tocar nada, las tarjetas pasan a **Confirmada** (también puedes
   forzarla con el botón Sincronizar).
6. **Mapa de calor:** abre http://localhost:5080/mapa y elige el mismo desastre. Los puntos aparecen en segundos;
   las zonas con niveles altos se ven más rojas.

Solo se pintan las evaluaciones **con desastre**. Las que se guardan sin desastre quedan como *Preliminar* y no
salen en el mapa.

**Conflictos (opcional, dos dispositivos):** A crea una evaluación y sincroniza. B descarga, evalúa el mismo activo y
sincroniza. A edita la suya sin volver a descargar. A queda **En conflicto** y el servidor conserva ambas versiones.

## 4. Compilar y probar desde el código

App (Android Studio, o desde la raíz):

```bash
./gradlew assembleDebug testDebugUnitTest
```

Con un emulador abierto y el sistema central arriba, las pruebas instrumentadas incluyen la prueba de contrato contra
el API real:

```bash
./gradlew connectedDebugAndroidTest
```

API (requiere .NET 10 SDK y PostgreSQL; ver [api/README.md](api/README.md)):

```bash
cd api
dotnet test
```

## 5. Estructura

```
├── app/                  App Android (presentacion · acceso · dominio · infraestructura)
├── api/                  API .NET, pruebas y simulador de QA-14
├── docs/DISENO.md        Arquitectura, modelo de datos, sincronización y mapa de calor
├── entrega/              APK listo para instalar
└── docker-compose.yml    API + PostgreSQL para pruebas
```
