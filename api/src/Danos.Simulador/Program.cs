using System.Diagnostics;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Security.Cryptography;
using System.Text.Json;

// Simulador de QA-14: 120 evaluaciones hechas sin conexión, 5 de ellas en conflicto, enviadas en lotes de 20
// y reenviadas completas. Uso: dotnet run --project src/Danos.Simulador -- --url http://localhost:5080
// Es repetible: cada corrida crea sus propios 120 activos, usa dispositivos y GUID nuevos y toma como base
// la versión descargada. OJO: escribe datos simulados en la base del API; córralo contra una base aparte
// (ver README) para no mezclarlos con los que llegan de la app.

const int Total = 120;
const int EnConflicto = 5;
const int TamanoLote = 20;
var limite = TimeSpan.FromMinutes(10);

string url = "http://localhost:5080";
for (int i = 0; i < args.Length - 1; i++)
{
    if (args[i] == "--url")
    {
        url = args[i + 1];
    }
}

var json = new JsonSerializerOptions(JsonSerializerDefaults.Web);
string corrida = Guid.NewGuid().ToString("N")[..8];
string dispositivoA = $"sim-A-{corrida}";
string dispositivoB = $"sim-B-{corrida}";
var reloj = Stopwatch.StartNew();

Console.WriteLine($"Simulador QA-14 contra {url} · corrida {corrida}");

using var http = Cliente(dispositivoA);
var catalogo = await http.GetFromJsonAsync<Descarga>("/v1/descarga", json)
    ?? throw new InvalidOperationException("La descarga vino vacía");
if (catalogo.TiposActivo.Count == 0 || catalogo.Desastres.Count == 0)
{
    throw new InvalidOperationException("El API no tiene catálogo o desastres sembrados");
}
var desastreId = catalogo.Desastres[0].Id;

// 0. Preparación: un dispositivo aparte crea en campo los 120 activos que se van a evaluar (el API ya no
//    siembra activos). Cuadrícula de 12 × 10 con ~220 m entre puntos y un corrimiento al azar por corrida.
string dispositivoP = $"sim-P-{corrida}";
using var httpP = Cliente(dispositivoP);
var origenLat = 4.58 + Random.Shared.NextDouble() * 0.002;
var origenLon = -74.14 + Random.Shared.NextDouble() * 0.002;
var nuevos = new HashSet<Guid>();
var opsP = new List<Operacion>();
for (int i = 0; i < Total; i++)
{
    var tipo = catalogo.TiposActivo[i % catalogo.TiposActivo.Count].Codigo;
    double lat = Math.Round(origenLat + (i / 12) * 0.002, 6);
    double lon = Math.Round(origenLon + (i % 12) * 0.002, 6);
    var id = Guid.NewGuid();
    nuevos.Add(id);
    var nuevo = new ActivoNuevo(id, tipo, lat, lon, $"Activo simulado {i + 1:000} · corrida {corrida}");
    var evaluacion = new EvaluacionEntrante(Guid.NewGuid(), id, null, DateTime.UtcNow.AddHours(-2), "Preparación",
        "Simulador QA-14", lat, lon, 5.0, tipo, "SIN_DANO", "Registro inicial del activo", [], nuevo);
    opsP.Add(new Operacion(Guid.NewGuid(), "CREAR", null, evaluacion));
}
foreach (var lote in opsP.Chunk(TamanoLote))
{
    await Sincronizar(httpP, dispositivoP, lote.ToList());
}
Console.WriteLine($"0. Preparación: {Total} activos nuevos creados en campo por {dispositivoP}.");

// 1. Descarga previa (la misma para los dos dispositivos, antes de que B cambie nada).
var descarga = await http.GetFromJsonAsync<Descarga>("/v1/descarga", json)
    ?? throw new InvalidOperationException("La descarga vino vacía");
var activos = descarga.Activos.Where(a => nuevos.Contains(a.Id)).OrderBy(a => a.Referencia).ToList();
if (activos.Count < Total)
{
    throw new InvalidOperationException($"Se crearon {Total} activos y la descarga solo trae {activos.Count}");
}
Console.WriteLine($"1. Descarga: {descarga.Activos.Count} activos, {descarga.TiposActivo.Count} tipos, {descarga.Desastres.Count} desastres.");

// 2. El dispositivo B evalúa 5 de esos activos primero: sube su versión en el servidor.
using var httpB = Cliente(dispositivoB);
var opsB = new List<Operacion>();
for (int i = 0; i < EnConflicto; i++)
{
    opsB.Add(NuevaOperacion(activos[i], "Bruno Díaz", "LEVE", foto: null));
}
var respuestaB = await Sincronizar(httpB, dispositivoB, opsB);
int aplicadasB = respuestaB.Resultados.Count(r => r.Resultado == "APLICADA");
Console.WriteLine($"2. Dispositivo B: {aplicadasB}/{EnConflicto} aplicadas (suben la versión de esos activos).");

// 3. El dispositivo A envía 120 CREAR con la versión que descargó ANTES del paso 2.
var fotos = new Dictionary<Guid, byte[]>();
var operacionesA = new List<Operacion>();
for (int i = 0; i < Total; i++)
{
    var foto = FotoJpeg();
    var op = NuevaOperacion(activos[i], "Ana Pérez", i % 2 == 0 ? "GRAVE" : "MODERADO", foto);
    fotos[op.Evaluacion.Evidencias[0].Id] = foto;
    operacionesA.Add(op);
}
var lotes = operacionesA.Chunk(TamanoLote).Select(c => c.ToList()).ToList();
var primeras = new List<RespuestaLote>();
foreach (var lote in lotes)
{
    primeras.Add(await Sincronizar(http, dispositivoA, lote));
}
var resultadosA = primeras.SelectMany(r => r.Resultados).ToList();
Console.WriteLine($"3. Dispositivo A: {lotes.Count} lotes de {TamanoLote} enviados.");

int subidas = 0;
foreach (var (evidenciaId, foto) in fotos)
{
    using var contenido = new MultipartFormDataContent();
    var parte = new ByteArrayContent(foto);
    parte.Headers.ContentType = new MediaTypeHeaderValue("image/jpeg");
    contenido.Add(parte, "archivo", $"{evidenciaId}.jpg");
    var respuesta = await http.PutAsync($"/v1/evidencias/{evidenciaId}", contenido);
    if (respuesta.IsSuccessStatusCode)
    {
        subidas++;
    }
}
Console.WriteLine($"   Fotos subidas: {subidas}/{fotos.Count}.");

// 4. Reenvío de exactamente los mismos lotes (como si la red se hubiera caído antes de la respuesta).
var segundas = new List<RespuestaLote>();
foreach (var lote in lotes)
{
    segundas.Add(await Sincronizar(http, dispositivoA, lote));
}
Console.WriteLine("4. Reenvío de los mismos lotes completado.");

// 5. Verificación contra el servidor.
var evaluaciones = await http.GetFromJsonAsync<List<EvaluacionResumen>>($"/v1/evaluaciones?idDispositivo={dispositivoA}", json) ?? [];
var conflictos = await http.GetFromJsonAsync<List<ConflictoResumen>>("/v1/conflictos?estado=PENDIENTE", json) ?? [];
reloj.Stop();

var idsA = operacionesA.Select(o => o.Evaluacion.Id).ToHashSet();
var activosB = activos.Take(EnConflicto).Select(a => a.Id).ToHashSet();
var conflictosA = conflictos.Where(c => idsA.Contains(c.Entrante.EvaluacionId)).ToList();
int conflictosDetectados = conflictosA.Count(c => activosB.Contains(c.ActivoId) && c.Vigente != null);
var mismosResultados = JsonSerializer.Serialize(primeras, json) == JsonSerializer.Serialize(segundas, json);
int aplicadas = resultadosA.Count(r => r.Resultado == "APLICADA");
int enConflicto = resultadosA.Count(r => r.Resultado == "EN_CONFLICTO");
bool conflictoEnLosDeB = resultadosA.Take(EnConflicto).All(r => r.Resultado == "EN_CONFLICTO");
int perdidas = idsA.Count(id => evaluaciones.All(e => e.Id != id));
int duplicadas = evaluaciones.Count - evaluaciones.Select(e => e.Id).Distinct().Count()
                 + evaluaciones.Count(e => e.NumeroVersiones != 1)
                 + (conflictosA.Count - conflictosA.Select(c => c.Entrante.EvaluacionId).Distinct().Count());
int evidenciasSubidas = evaluaciones.Sum(e => e.EvidenciasSubidas);

var filas = new List<(string Criterio, string Esperado, string Obtenido, bool Pasa)>
{
    ("Evaluaciones APLICADA", $"{Total - EnConflicto}", $"{aplicadas}", aplicadas == Total - EnConflicto),
    ("Evaluaciones EN_CONFLICTO", $"{EnConflicto}", $"{enConflicto}", enConflicto == EnConflicto && conflictoEnLosDeB),
    ("Conflictos detectados (ambas versiones)", $"{EnConflicto} (100 %)", $"{conflictosDetectados}", conflictosDetectados == EnConflicto),
    ("Pérdidas", "0", $"{perdidas}", perdidas == 0 && evaluaciones.Count == Total),
    ("Duplicados tras el reenvío", "0", $"{duplicadas}", duplicadas == 0),
    ("Resultados idénticos en el reenvío", "sí", mismosResultados ? "sí" : "no", mismosResultados),
    ("Evidencias subidas", $"{Total}", $"{evidenciasSubidas}", evidenciasSubidas == Total && subidas == Total),
    ("Tiempo total", "≤ 10 min", $"{reloj.Elapsed.TotalSeconds:0.0} s", reloj.Elapsed <= limite),
};

Console.WriteLine();
Console.WriteLine($"{"Criterio QA-14",-42} {"Esperado",-12} {"Obtenido",-12} Resultado");
Console.WriteLine(new string('-', 80));
foreach (var f in filas)
{
    Console.WriteLine($"{f.Criterio,-42} {f.Esperado,-12} {f.Obtenido,-12} {(f.Pasa ? "PASA" : "FALLA")}");
}
bool todo = filas.All(f => f.Pasa);
Console.WriteLine(new string('-', 80));
Console.WriteLine(todo ? "QA-14: PASA" : "QA-14: FALLA");
return todo ? 0 : 1;

// ---------- Funciones auxiliares ----------

HttpClient Cliente(string dispositivo)
{
    var c = new HttpClient { BaseAddress = new Uri(url), Timeout = TimeSpan.FromSeconds(60) };
    c.DefaultRequestHeaders.Add("X-Usuario", "simulador");
    c.DefaultRequestHeaders.Add("X-Organizacion", "Simulador QA-14");
    c.DefaultRequestHeaders.Add("X-Dispositivo", dispositivo);
    return c;
}

Operacion NuevaOperacion(Activo activo, string evaluador, string nivel, byte[]? foto)
{
    var evidencias = new List<EvidenciaDeclarada>();
    if (foto != null)
    {
        evidencias.Add(new EvidenciaDeclarada(Guid.NewGuid(), activo.Lat, activo.Lon, DateTime.UtcNow.AddMinutes(-20),
            Convert.ToHexStringLower(SHA256.HashData(foto)), foto.Length));
    }
    var evaluacion = new EvaluacionEntrante(Guid.NewGuid(), activo.Id, desastreId, DateTime.UtcNow.AddMinutes(-20),
        evaluador, "Cruz Roja", activo.Lat, activo.Lon, 5.0, activo.TipoCodigo, nivel,
        $"Evaluación simulada de {activo.Referencia}", evidencias, null);
    return new Operacion(Guid.NewGuid(), "CREAR", activo.Version, evaluacion);
}

async Task<RespuestaLote> Sincronizar(HttpClient cliente, string dispositivo, List<Operacion> operaciones)
{
    var lote = new Lote(dispositivo, DateTime.UtcNow, operaciones);
    var respuesta = await cliente.PostAsJsonAsync("/v1/sincronizacion/evaluaciones", lote, json);
    var texto = await respuesta.Content.ReadAsStringAsync();
    if (!respuesta.IsSuccessStatusCode)
    {
        throw new InvalidOperationException($"HTTP {(int)respuesta.StatusCode}: {texto}");
    }
    return JsonSerializer.Deserialize<RespuestaLote>(texto, json)!;
}

static byte[] FotoJpeg()
{
    var bytes = new byte[32 * 1024];
    Random.Shared.NextBytes(bytes);
    bytes[0] = 0xFF; bytes[1] = 0xD8; bytes[2] = 0xFF; bytes[3] = 0xE0;
    bytes[^2] = 0xFF; bytes[^1] = 0xD9;
    return bytes;
}

// ---------- Contrato (solo lo que el simulador usa) ----------

record Descarga(List<TipoActivo> TiposActivo, List<Desastre> Desastres, List<Activo> Activos);
record TipoActivo(string Codigo, string Nombre);
record Desastre(Guid Id, string Nombre);
record Activo(Guid Id, string TipoCodigo, double Lat, double Lon, string Referencia, int Version);
record Lote(string IdDispositivo, DateTime InstanteEnvioDispositivo, List<Operacion> Operaciones);
record Operacion(Guid IdOperacion, string Tipo, int? VersionBaseActivo, EvaluacionEntrante Evaluacion);
record EvaluacionEntrante(Guid Id, Guid ActivoId, Guid? DesastreId, DateTime InstanteCampo, string Evaluador,
    string Organizacion, double Lat, double Lon, double PrecisionM, string TipoActivoCodigo, string NivelDano,
    string Descripcion, List<EvidenciaDeclarada> Evidencias, ActivoNuevo? ActivoNuevo);
record ActivoNuevo(Guid Id, string TipoCodigo, double Lat, double Lon, string Referencia);
record EvidenciaDeclarada(Guid Id, double Lat, double Lon, DateTime InstanteCaptura, string Sha256, long TamanoBytes);
record RespuestaLote(List<ResultadoOperacion> Resultados);
record ResultadoOperacion(Guid IdOperacion, string Resultado, string? EstadoEvaluacion, string? Motivo,
    int? NuevaVersionActivo, DateTime InstanteServidor);
record EvaluacionResumen(Guid Id, string Estado, int NumeroVersiones, int EvidenciasDeclaradas, int EvidenciasSubidas);
record ConflictoResumen(Guid Id, Guid ActivoId, string Tipo, VersionResumen? Vigente, VersionResumen Entrante);
record VersionResumen(Guid EvaluacionId, int NumeroVersion, string IdDispositivo);
