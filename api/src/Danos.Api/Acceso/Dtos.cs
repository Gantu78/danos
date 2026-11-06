using Danos.Api.Dominio;

namespace Danos.Api.Acceso;

// Contrato REST con la app móvil. JSON en camelCase, fechas ISO-8601 UTC y enums como texto.
// Lo que llega del celular se declara con tipos que aceptan null: así un atributo faltante o un valor
// raro no tumba el lote entero con un 400, sino que la operación sale INVALIDA con su motivo.

public record ErrorDto(string Error);

public record CriterioDto(NivelDano Nivel, string Criterio);

public record TipoActivoDto(string Codigo, string Nombre, List<CriterioDto> Criterios);

public record DesastreDto(Guid Id, string Nombre, string Tipo);

public record ActivoDto(Guid Id, string TipoCodigo, double Lat, double Lon, string Referencia, int Version);

public record DescargaDto(
    DateTime InstanteServidor,
    List<TipoActivoDto> TiposActivo,
    List<DesastreDto> Desastres,
    List<ActivoDto> Activos);

// ---------- Sincronización ----------

public class LoteSincronizacionDto
{
    public string? IdDispositivo { get; set; }
    public DateTimeOffset? InstanteEnvioDispositivo { get; set; }
    public List<OperacionDto>? Operaciones { get; set; }
}

public class OperacionDto
{
    public Guid? IdOperacion { get; set; }
    public string? Tipo { get; set; }
    public int? VersionBaseActivo { get; set; }
    public EvaluacionEntranteDto? Evaluacion { get; set; }
}

public class EvaluacionEntranteDto
{
    public Guid? Id { get; set; }
    public Guid? ActivoId { get; set; }
    public ActivoNuevoDto? ActivoNuevo { get; set; }
    public Guid? DesastreId { get; set; }
    public DateTimeOffset? InstanteCampo { get; set; }
    public string? Evaluador { get; set; }
    public string? Organizacion { get; set; }
    public double? Lat { get; set; }
    public double? Lon { get; set; }
    public double? PrecisionM { get; set; }
    public string? TipoActivoCodigo { get; set; }
    public string? NivelDano { get; set; }
    public string? Descripcion { get; set; }
    public List<EvidenciaDeclaradaDto>? Evidencias { get; set; }
}

public class ActivoNuevoDto
{
    public Guid? Id { get; set; }
    public string? TipoCodigo { get; set; }
    public double? Lat { get; set; }
    public double? Lon { get; set; }
    public string? Referencia { get; set; }
}

public class EvidenciaDeclaradaDto
{
    public Guid? Id { get; set; }
    public double? Lat { get; set; }
    public double? Lon { get; set; }
    public DateTimeOffset? InstanteCaptura { get; set; }
    public string? Sha256 { get; set; }
    public long? TamanoBytes { get; set; }
}

public record ResultadoOperacionDto(
    Guid IdOperacion,
    ResultadoOperacion Resultado,
    EstadoEvaluacion? EstadoEvaluacion,
    string? Motivo,
    int? NuevaVersionActivo,
    DateTime InstanteServidor);

public record RespuestaSincronizacionDto(List<ResultadoOperacionDto> Resultados);

// ---------- Evidencias ----------

public record EvidenciaSubidaDto(Guid Id, Guid EvaluacionId, bool Subida, DateTime? InstanteSubida, long TamanoBytes);

// ---------- Revisión (no los usa la app) ----------

public record VersionEvaluacionDto(
    Guid VersionId,
    Guid EvaluacionId,
    int NumeroVersion,
    string Autor,
    string IdDispositivo,
    int? VersionActivoBase,
    bool EnConflicto,
    DateTime Instante,
    System.Text.Json.JsonElement Contenido);

public record ConflictoDto(
    Guid Id,
    Guid ActivoId,
    TipoConflicto Tipo,
    EstadoConflicto Estado,
    string Regla,
    DateTime Instante,
    VersionEvaluacionDto? Vigente,
    VersionEvaluacionDto Entrante);

public record EvaluacionResumenDto(
    Guid Id,
    Guid ActivoId,
    Guid? DesastreId,
    string IdDispositivo,
    EstadoEvaluacion Estado,
    NivelDano NivelDano,
    DateTime InstanteCampo,
    DateTime InstanteServidor,
    int NumeroVersiones,
    int EvidenciasDeclaradas,
    int EvidenciasSubidas);

// ---------- Mapa de calor ----------

public record PuntoCalorDto(
    Guid ActivoId,
    string TipoActivoCodigo,
    double Lat,
    double Lon,
    NivelDano Nivel,
    double Peso,
    bool EnConflicto);

public record MapaCalorDto(
    Guid DesastreId,
    NivelDano NivelMinimo,
    DateTime? InstanteDato,
    DateTime InstanteServidor,
    int ConflictosPendientes,
    Dictionary<string, int> ConteoPorNivel,
    List<PuntoCalorDto> Puntos);
