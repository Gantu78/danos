# Documento de diseño — Evaluación de daños en sitios de desastre

Carlos Daniel Guiza, Juan Felipe Rubiano, Samuel Gantiva · Noviembre de 2026

Versión en PDF: [DISENO.pdf](DISENO.pdf). Fuente LaTeX: [latex/](latex/).

## 1. Descripción general

El sistema permite registrar la posición (latitud y longitud) y el nivel de afectación (de 1 a 5) de
sitios afectados por un desastre. Tiene tres partes:

- Una app Android que funciona sin internet y guarda todo en el teléfono.
- Un sistema central (API en .NET con PostgreSQL) que recibe la información cuando vuelve la red.
- Una página con un mapa de calor que muestra las zonas más afectadas de cada desastre.

Lo más importante del diseño es que no se pierda ni se sobrescriba información al sincronizar, aunque
varios evaluadores trabajen sin red sobre el mismo sitio. Para eso la app guarda una cola de operaciones y
el servidor detecta los conflictos sin resolverlos.

El diseño se describe con el modelo de vistas 4+1 de Kruchten, más un diagrama de contexto.

## 2. Diagrama de contexto

El evaluador usa la app en campo y la app toma la posición del GPS del teléfono. Cuando hay red, la app
descarga el catálogo y sincroniza con el sistema central. El coordinador ve el mapa de calor desde un
navegador; el mapa base sale de OpenStreetMap.

```mermaid
flowchart LR
    Evaluador(["Evaluador de campo"])
    Coordinador(["Coordinador de emergencia"])
    subgraph Sistema["Sistema de evaluación de daños"]
        App["App móvil DanosCampo<br/>Android · offline-first"]
        API["Sistema central<br/>API Evaluación de Daños"]
        Mapa["Mapa de calor<br/>página web"]
    end
    OSM[("OpenStreetMap<br/>teselas del mapa")]
    GPS[("GNSS del teléfono")]
    Evaluador -- "registra posición, nivel 1-5 y fotos" --> App
    GPS -- "lat, lon, precisión" --> App
    App -- "descarga y sincroniza cuando hay red" --> API
    Coordinador -- "consulta regiones afectadas" --> Mapa
    Mapa -- "puntos por desastre" --> API
    Mapa -- "teselas" --> OSM
```

## 3. Vista lógica

```mermaid
classDiagram
    direction LR
    class Evaluacion {
        +UUID id
        +Instant instanteCampo
        +String evaluador
        +String organizacion
        +double lat
        +double lon
        +double precisionM
        +NivelDano nivelDano
        +String descripcion
        +EstadoEvaluacion estadoEvaluacion
        +EstadoSync estadoSync
        +String motivoInvalida
    }
    class Activo {
        +UUID id
        +String referencia
        +double lat
        +double lon
        +int version
    }
    class TipoActivo {
        +String codigo
        +String nombre
    }
    class CriterioNivel {
        +NivelDano nivel
        +String criterio
    }
    class Desastre {
        +UUID id
        +String nombre
        +String tipo
    }
    class Evidencia {
        +UUID id
        +double lat
        +double lon
        +Instant instanteCaptura
        +String sha256
        +long tamanoBytes
        +bool subida
    }
    class Operacion {
        +UUID idOperacion
        +TipoOperacion tipo
        +int versionBaseActivo
        +String payloadJson
        +int intentos
    }
    class Conflicto {
        +TipoConflicto tipo
        +String regla
        +EstadoConflicto estado
    }
    class NivelDano {
        <<enumeration>>
        1 SIN_DANO
        2 LEVE
        3 MODERADO
        4 GRAVE
        5 DESTRUCCION_TOTAL
    }
    class EstadoEvaluacion {
        <<enumeration>>
        PRELIMINAR
        REGISTRADA
        EN_CONFLICTO
        VALIDADA
        RECHAZADA
    }
    class EstadoSync {
        <<enumeration>>
        PENDIENTE
        CONFIRMADA
        INVALIDA
    }
    Activo "1" <-- "*" Evaluacion : evalúa
    TipoActivo "1" <-- "*" Activo : clasifica
    TipoActivo "1" *-- "5" CriterioNivel : define
    Desastre "0..1" <-- "*" Evaluacion : agrupa
    Evaluacion "1" *-- "*" Evidencia : tiene
    Evaluacion "1" <-- "*" Operacion : cola de salida
    Conflicto "*" --> "1" Activo : sobre
    Conflicto "*" --> "1..2" Evaluacion : vigente y entrante
    Evaluacion ..> NivelDano
    Evaluacion ..> EstadoEvaluacion
    Evaluacion ..> EstadoSync
```

- **Evaluación**: lo que registra el evaluador sobre un activo (posición, precisión, nivel, descripción y
  fotos). Su id es un UUID generado en el teléfono.
- **Activo**: el sitio o bien evaluado (vivienda, escuela, puente, etc.). Tiene una versión que aumenta
  cada vez que el servidor acepta una evaluación sobre él.
- **TipoActivo** y **CriterioNivel**: catálogo que se descarga; cada tipo describe qué significa cada nivel
  de 1 a 5.
- **Desastre**: agrupa evaluaciones. Si una evaluación no tiene desastre queda como preliminar y no sale en
  el mapa de calor.
- **Evidencia**: foto con su ubicación, fecha y hash SHA-256.
- **Operación**: elemento de la cola de envío (crear o actualizar) con una copia completa de la evaluación.
- **Conflicto**: lo crea el servidor cuando una evaluación choca con otra.

Cada evaluación tiene dos estados. El estado de sincronización es solo local: `PENDIENTE` mientras no hay
respuesta del servidor, `CONFIRMADA` cuando el servidor la recibió e `INVALIDA` si la rechazó. El estado de
la evaluación lo decide el servidor: `PRELIMINAR`, `REGISTRADA`, `EN_CONFLICTO`, `VALIDADA` o `RECHAZADA`.

**Reglas**

- Una operación solo se borra de la cola cuando el servidor respondió por ella.
- Una operación que ya se intentó enviar no se modifica. Si el usuario edita después, se crea una operación
  nueva.
- La app nunca resuelve conflictos, solo muestra lo que dice el servidor.

**Conflicto.** Cada operación lleva la versión del activo que tenía el teléfono. Hay conflicto si el activo
tiene una versión mayor hecha por otro dispositivo; las versiones del mismo dispositivo no cuentan. Cuando
hay conflicto el servidor guarda las dos versiones y no cambia la vigente.

**Duplicados.** Un activo nuevo se marca como posible duplicado si hay otra evaluación a menos de 25 m
(distancia de Haversine) en un rango de 24 horas.

**Mapa de calor.** Se toma un punto por activo con el nivel de su evaluación vigente. El peso es 0, 0,25,
0,5, 0,75 y 1 para los niveles 1 a 5.

## 4. Vista de procesos

```mermaid
sequenceDiagram
    autonumber
    actor E as Evaluador
    participant App as App (UI + ViewModel)
    participant DB as Room (réplica + cola)
    participant W as SincronizacionWorker
    participant API as API Evaluación de Daños
    participant PG as PostgreSQL
    E->>App: registra posición, nivel 1-5, descripción y fotos
    App->>DB: guarda evaluación + evidencias + operación CREAR (una transacción)
    App->>W: encola trabajo único con restricción CONNECTED
    Note over E,DB: sin red todo termina aquí: la evaluación queda PENDIENTE
    Note over W: al volver la red, WorkManager ejecuta el trabajo
    loop mientras haya operaciones y avance
        W->>DB: toma ≤ 20 operaciones e incrementa intentos
        W->>API: POST /v1/sincronizacion/evaluaciones
        loop cada operación (transacción propia)
            API->>PG: ¿idOperacion ya procesada? → devuelve el mismo resultado
            API->>API: valida · regla de conflicto · posibles duplicados
            API->>PG: guarda evaluación o versión en conflicto + resultado
        end
        alt 2xx
            API-->>W: APLICADA · EN_CONFLICTO · POSIBLE_DUPLICADO · INVALIDA
            W->>DB: aplica cada resultado y retira la operación
        else sin red o 5xx
            W-->>W: reintento con espera exponencial (30 s)
        else 4xx
            W->>DB: guarda ultimoError y se detiene
        end
    end
    W->>API: PUT /v1/evidencias/{id} (fotos de evaluaciones confirmadas)
    API-->>W: 2xx subida · 422 hash distinto
    App-->>E: la tarjeta pasa a Confirmada (o En conflicto)
```

1. Al guardar, la app escribe en una sola transacción la evaluación, las fotos y una operación en la cola,
   y programa un trabajo de WorkManager que solo se ejecuta cuando hay red. Sin red el proceso termina ahí
   y la evaluación queda pendiente.
2. Cuando vuelve la red, el trabajo toma hasta 20 operaciones y las envía en un lote.
3. El servidor procesa cada operación en su propia transacción. Si ya había recibido esa operación (mismo
   `idOperacion`) devuelve la misma respuesta, así que reenviar no duplica nada.
4. La app aplica cada resultado: `APLICADA` confirma la evaluación, `EN_CONFLICTO` y `POSIBLE_DUPLICADO`
   la confirman pero la marcan en conflicto, e `INVALIDA` muestra el motivo para que el usuario la corrija.
5. Al final se suben las fotos de las evaluaciones confirmadas.

Si no hay red o el servidor responde con error 5xx, el trabajo se reintenta más tarde con espera
exponencial. Si responde con error 4xx, se guarda el error y no se reintenta en bucle.

## 5. Vista de desarrollo

El repositorio tiene dos proyectos: la app y el API. Los dos están organizados por capas y solo se
comunican por el contrato REST.

```mermaid
flowchart LR
    subgraph Repo["Repositorio danos"]
        direction LR
        subgraph APP["app/ · Android (Kotlin)"]
            direction TB
            P["presentacion<br/>Compose · ViewModel · navegación"]
            A["acceso<br/>repositorios"]
            I["infraestructura<br/>local (Room) · remoto (Retrofit) · sync (WorkManager)<br/>dispositivo · preferencias · di (Hilt)"]
            D["dominio<br/>modelos · NivelDano · ValidadorEvaluacion"]
            P --> A --> I
            P -.-> D
            A -.-> D
            I -.-> D
        end
        subgraph SRV["api/ · ASP.NET Core (.NET 10)"]
            direction TB
            AC["Acceso<br/>controladores · DTO · FiltroEncabezados · /mapa"]
            DO["Dominio<br/>servicios · ReglaConflicto · DetectorDuplicados · validador"]
            IN["Infraestructura<br/>EF Core · migraciones · semilla · archivos"]
            AC --> DO --> IN
        end
        DOCS["docs/ · diseño"]
        DC["docker-compose.yml"]
    end
    APP -- "contrato REST JSON" --> SRV
    DC -. "construye" .-> SRV
```

**App (Kotlin).** `presentacion` tiene las pantallas en Jetpack Compose y los ViewModel; `acceso` tiene los
repositorios, que son la única forma de llegar a los datos; `dominio` tiene los modelos y validaciones sin
dependencias de Android; `infraestructura` tiene Room, Retrofit, WorkManager, GPS y cámara. Las
dependencias se inyectan con Hilt.

**API (.NET 10).** `Acceso` tiene los controladores y la página del mapa; `Dominio` tiene los servicios y
las reglas de conflicto y duplicados; `Infraestructura` tiene Entity Framework Core y el almacenamiento de
fotos.

| Endpoint | Uso |
| --- | --- |
| `GET /v1/descarga` | Catálogo, desastres y activos |
| `POST /v1/sincronizacion/evaluaciones` | Recibe un lote de operaciones |
| `PUT /v1/evidencias/{id}` | Recibe una foto |
| `GET /v1/mapa-calor` | Puntos del mapa de calor |
| `GET /mapa` | Página del mapa de calor |

## 6. Vista física

```mermaid
flowchart LR
    subgraph Tel["Celular o tablet · Android 8+"]
        APK["DanosCampo.apk"]
        SQLite[("SQLite<br/>réplica + cola")]
        Fotos[("filesDir/evidencias")]
        APK --- SQLite
        APK --- Fotos
    end
    subgraph Srv["PC o servidor · Docker Compose"]
        subgraph CA["contenedor api"]
            Kestrel["ASP.NET Core<br/>Kestrel :5080"]
            VolE[("volumen evidencias")]
        end
        subgraph CB["contenedor db"]
            PG[("PostgreSQL 18<br/>volumen datos")]
        end
        Kestrel -- "TCP 5432" --> PG
        Kestrel --- VolE
    end
    Nav["Navegador del coordinador"]
    APK -- "HTTP :5080 · Wi-Fi o datos<br/>(10.0.2.2 desde el emulador)" --> Kestrel
    Nav -- "HTTP :5080/mapa" --> Kestrel
```

La app corre en el celular o tablet y guarda sus datos en SQLite. El sistema central corre con Docker
Compose en dos contenedores: uno con el API (puerto 5080) y otro con PostgreSQL. El coordinador abre el
mapa desde el navegador. Desde el emulador de Android el servidor se ve en `10.0.2.2:5080`; desde un
celular se usa la IP del PC en la misma red Wi-Fi.

## 7. Escenarios

```mermaid
flowchart LR
    Ev(["Evaluador"])
    Co(["Coordinador"])
    subgraph Sistema["Sistema de evaluación de daños"]
        direction TB
        CU1(["CU-01 Configurar y probar conexión"])
        CU2(["CU-02 Descargar catálogo y activos"])
        CU3(["CU-03 Registrar evaluación sin red"])
        CU4(["CU-04 Sincronizar al volver la red"])
        CU5(["CU-05 Editar evaluación propia"])
        CU6(["CU-06 Detectar conflicto o duplicado"])
        CU7(["CU-07 Consultar mapa de calor"])
    end
    Ev --- CU1
    Ev --- CU2
    Ev --- CU3
    Ev --- CU5
    CU3 -. "include" .-> CU4
    CU5 -. "include" .-> CU4
    CU6 -. "extend" .-> CU4
    Co --- CU7
```

- **CU-01 Configurar**: el evaluador escribe su nombre, organización y la URL del servidor, y puede probar
  la conexión.
- **CU-02 Descargar**: con red, descarga tipos de activo, desastres y activos.
- **CU-03 Registrar sin red**: elige o crea el activo, la app toma el GPS, elige el nivel de 1 a 5, el
  desastre, una descripción y fotos, y guarda.
- **CU-04 Sincronizar**: al volver la red las evaluaciones se envían solas.
- **CU-05 Editar**: modifica una evaluación propia y se vuelve a sincronizar.
- **CU-06 Detectar conflicto**: el servidor detecta conflictos o duplicados y guarda ambas versiones.
- **CU-07 Ver mapa de calor**: el coordinador elige un desastre y ve las zonas más afectadas.

El escenario CU-03 + CU-04 recorre todas las vistas: usa las clases Evaluación y Operación (lógica), el
trabajo de WorkManager y el servidor (procesos), las capas de la app y del API (desarrollo) y los dos nodos
(física).

## 8. Decisiones de diseño

- Se guarda una copia completa de los datos en el teléfono para poder trabajar sin internet.
- Los ids se generan en el teléfono (UUID) para poder crear registros sin red.
- El servidor detecta los conflictos y no los resuelve, para no perder la versión de nadie.
- El conflicto se decide por la versión del activo y no por la hora del teléfono, porque los relojes de los
  celulares pueden estar mal.
- Las fotos se suben aparte del lote para que el envío sea liviano.
- El sistema central se despliega con Docker Compose para levantarlo con un solo comando.
