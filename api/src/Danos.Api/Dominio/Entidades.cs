namespace Danos.Api.Dominio;

// Entidades de EF Core (como las @Entity de JPA). El mapeo a tablas está en Infraestructura/DanosDbContext
// con Fluent API, para que el dominio no dependa de anotaciones de persistencia.

public class TipoActivo
{
    public string Codigo { get; set; } = "";
    public string Nombre { get; set; } = "";
    public List<CriterioNivel> Criterios { get; set; } = [];
}

public class CriterioNivel
{
    public string TipoActivoCodigo { get; set; } = "";
    public NivelDano Nivel { get; set; }
    public string Criterio { get; set; } = "";
}

public class Desastre
{
    public Guid Id { get; set; }
    public string Nombre { get; set; } = "";
    public string Tipo { get; set; } = "";
}

public class Activo
{
    public Guid Id { get; set; }
    public string TipoActivoCodigo { get; set; } = "";
    public double Lat { get; set; }
    public double Lon { get; set; }
    public string Referencia { get; set; } = "";

    /// <summary>Token de concurrencia: sube cada vez que se aplica una evaluación del activo.</summary>
    public int Version { get; set; }

    public string CreadoPorDispositivo { get; set; } = "";
}

/// <summary>Qué dispositivo produjo cada versión del activo. Es lo que mira la regla de conflictos.</summary>
public class ActivoVersion
{
    public Guid ActivoId { get; set; }
    public int Numero { get; set; }
    public string IdDispositivo { get; set; } = "";
    public Guid? EvaluacionId { get; set; }
    public DateTime Instante { get; set; }
}

public class Evaluacion
{
    // Los 11 atributos del SRS (Cuadro 8)
    public Guid Id { get; set; }
    public Guid? DesastreId { get; set; }
    public DateTime InstanteCampo { get; set; }
    public string Evaluador { get; set; } = "";
    public string Organizacion { get; set; } = "";
    public double Lat { get; set; }
    public double Lon { get; set; }
    public string TipoActivoCodigo { get; set; } = "";
    public NivelDano NivelDano { get; set; }
    public string Descripcion { get; set; } = "";
    public EstadoEvaluacion Estado { get; set; }

    // Datos de la sincronización
    public Guid ActivoId { get; set; }
    public double PrecisionM { get; set; }
    public DateTime InstanteServidor { get; set; }
    public string IdDispositivo { get; set; } = "";
    public long DesfaseRelojMs { get; set; }
}

/// <summary>Historial de solo inserción (RF-ED-07). El DbContext rechaza Update y Delete.</summary>
public class EvaluacionVersion
{
    public Guid Id { get; set; }
    public Guid EvaluacionId { get; set; }
    public int NumeroVersion { get; set; }
    public string ContenidoJson { get; set; } = "{}";
    public string Autor { get; set; } = "";
    public string IdDispositivo { get; set; } = "";
    public int? VersionActivoBase { get; set; }
    public bool EnConflicto { get; set; }
    public DateTime Instante { get; set; }
}

public class Evidencia
{
    public Guid Id { get; set; }
    public Guid EvaluacionId { get; set; }
    public double Lat { get; set; }
    public double Lon { get; set; }
    public DateTime InstanteCaptura { get; set; }
    public string Sha256Declarado { get; set; } = "";
    public long TamanoBytes { get; set; }
    public bool Subida { get; set; }
    public string? RutaArchivo { get; set; }
    public DateTime? InstanteSubida { get; set; }
}

public class Conflicto
{
    public Guid Id { get; set; }
    public Guid ActivoId { get; set; }
    public TipoConflicto Tipo { get; set; }
    public Guid? EvaluacionVigenteId { get; set; }
    public Guid EvaluacionEntranteId { get; set; }
    public Guid VersionEntranteId { get; set; }
    public string Regla { get; set; } = "";
    public EstadoConflicto Estado { get; set; } = EstadoConflicto.PENDIENTE;
    public DateTime Instante { get; set; }
}

/// <summary>Resultado guardado de cada operación: el reenvío devuelve exactamente esto.</summary>
public class OperacionProcesada
{
    public Guid IdOperacion { get; set; }
    public string IdDispositivo { get; set; } = "";
    public string ResultadoJson { get; set; } = "{}";
    public DateTime Instante { get; set; }
}

/// <summary>Outbox: eventos EV-09 y EV-10 para cuando exista el bus. Por ahora solo se guardan.</summary>
public class EventoSaliente
{
    public long Id { get; set; }
    public string Tipo { get; set; } = "";
    public string PayloadJson { get; set; } = "{}";
    public DateTime Instante { get; set; }
    public bool Publicado { get; set; }
}
