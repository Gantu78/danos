using Danos.Api.Dominio;
using Microsoft.EntityFrameworkCore;

namespace Danos.Api.Infraestructura;

// Al arrancar en Development: aplica las migraciones y, si la base está vacía, carga solo lo que la app
// necesita para trabajar: el catálogo de tipos de activo con sus criterios por nivel y los desastres.
// No se siembran activos ni evaluaciones: los activos los crea la app en campo (activoNuevo) y llegan
// por sincronización. Los GUID de los desastres son fijos para que la app y las pruebas los conozcan.
public static class Semilla
{
    private const int Intentos = 10;
    private static readonly TimeSpan Espera = TimeSpan.FromSeconds(3);

    public static readonly Guid DesastreInundacion = Guid.Parse("d0000000-0000-0000-0000-000000000001");
    public static readonly Guid DesastreSismo = Guid.Parse("d0000000-0000-0000-0000-000000000002");
    public static readonly Guid DesastreDeslizamiento = Guid.Parse("d0000000-0000-0000-0000-000000000003");

    public static async Task EjecutarAsync(IServiceProvider servicios, ILogger logger)
    {
        using var scope = servicios.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<DanosDbContext>();

        for (int intento = 1; ; intento++)
        {
            try
            {
                await db.Database.MigrateAsync();
                break;
            }
            catch (Exception ex) when (intento < Intentos)
            {
                logger.LogWarning("La BD no responde (intento {Intento}/{Total}): {Mensaje}. Reintento en {Segundos} s.",
                    intento, Intentos, ex.Message, Espera.TotalSeconds);
                await Task.Delay(Espera);
            }
        }
        logger.LogInformation("Migraciones aplicadas.");

        var hayDatos = await (from t in db.TiposActivo select t.Codigo).AnyAsync();
        if (hayDatos)
        {
            return;
        }

        var tipos = Tipos();
        db.TiposActivo.AddRange(tipos);
        db.Desastres.AddRange(
            new Desastre { Id = DesastreInundacion, Nombre = "Inundación río Bogotá", Tipo = "INUNDACION" },
            new Desastre { Id = DesastreSismo, Nombre = "Sismo Sabana de Bogotá", Tipo = "SISMO" },
            new Desastre { Id = DesastreDeslizamiento, Nombre = "Deslizamiento en Usme", Tipo = "DESLIZAMIENTO" });

        await db.SaveChangesAsync();
        logger.LogInformation("Semilla cargada: {Tipos} tipos de activo con sus criterios y 3 desastres. " +
            "Sin activos: los crea la app en campo.", tipos.Count);
    }

    private static List<TipoActivo> Tipos() =>
    [
        Tipo("vivienda", "Vivienda",
            "Sin afectación visible",
            "Daños menores en acabados, vidrios o tejas",
            "Fisuras no estructurales",
            "Daño estructural, no habitable",
            "Colapso total de la vivienda"),
        Tipo("edificio_publico", "Edificio público",
            "Sin afectación visible",
            "Daños en acabados; el edificio sigue en servicio",
            "Fisuras no estructurales; servicio parcial",
            "Daño estructural; se cierra al público",
            "Colapso total o parcial del edificio"),
        Tipo("hospital", "Hospital",
            "Sin afectación; atención normal",
            "Daños menores; atención normal",
            "Daño en áreas no críticas; atención reducida",
            "Daño estructural o en áreas críticas; se evacúa",
            "Colapso; el hospital deja de operar"),
        Tipo("escuela", "Escuela",
            "Sin afectación visible",
            "Daños menores en acabados o mobiliario",
            "Fisuras no estructurales; aulas parcialmente utilizables",
            "Daño estructural; no se pueden dictar clases",
            "Colapso total de la edificación"),
        Tipo("puente", "Puente",
            "Sin afectación; tránsito normal",
            "Daños menores en barandas o capa de rodadura",
            "Daño en elementos no portantes",
            "Daño portante, sin tránsito",
            "Colapso del puente"),
        Tipo("carretera", "Carretera",
            "Sin afectación; tránsito normal",
            "Baches o derrumbes menores; tránsito normal",
            "Pérdida parcial de banca; paso a un carril",
            "Pérdida de banca o derrumbe; vía cerrada",
            "Tramo destruido; requiere reconstrucción"),
        Tipo("sistema_agua", "Sistema de agua",
            "Sin afectación; servicio normal",
            "Fugas menores; servicio normal",
            "Daño en redes secundarias; servicio intermitente",
            "Daño en conducción o planta; sin servicio",
            "Infraestructura destruida"),
        Tipo("red_electrica", "Red eléctrica",
            "Sin afectación; servicio normal",
            "Daños menores; cortes breves",
            "Postes o transformadores dañados; cortes por sectores",
            "Subestación o línea principal dañada; sin servicio",
            "Infraestructura destruida"),
        Tipo("red_telecomunicaciones", "Red de telecomunicaciones",
            "Sin afectación; servicio normal",
            "Daños menores; servicio degradado",
            "Antenas o cableado dañados; cobertura parcial",
            "Nodo principal dañado; sin servicio",
            "Infraestructura destruida"),
        Tipo("instalacion_industrial", "Instalación industrial",
            "Sin afectación; opera normal",
            "Daños menores; opera normal",
            "Daño en áreas no críticas; operación reducida",
            "Daño estructural o riesgo de derrame; se detiene",
            "Colapso o pérdida total de la instalación"),
        Tipo("otra_infraestructura", "Otra infraestructura crítica",
            "Sin afectación; opera normal",
            "Daños menores; opera normal",
            "Daño parcial; operación reducida",
            "Daño grave; deja de operar",
            "Destrucción total"),
    ];

    private static TipoActivo Tipo(string codigo, string nombre,
        string sinDano, string leve, string moderado, string grave, string destruccion)
    {
        string[] textos = [sinDano, leve, moderado, grave, destruccion];
        var tipo = new TipoActivo { Codigo = codigo, Nombre = nombre };
        var niveles = Enum.GetValues<NivelDano>();
        for (int i = 0; i < niveles.Length; i++)
        {
            tipo.Criterios.Add(new CriterioNivel { TipoActivoCodigo = codigo, Nivel = niveles[i], Criterio = textos[i] });
        }
        return tipo;
    }
}
