using Danos.Api.Acceso;
using Danos.Api.Infraestructura;
using Microsoft.EntityFrameworkCore;

namespace Danos.Api.Dominio;

/// <summary>
/// Un punto por activo, en sus coordenadas, con el nivel de su evaluación VIGENTE (la de la última versión
/// aplicada del activo). Si el activo tiene un conflicto pendiente cuenta la vigente y no la entrante,
/// para no contarlo dos veces. Peso: sin daño 0, leve 0,25, moderado 0,5, grave 0,75, destrucción total 1.
/// </summary>
public class ServicioMapaCalor(DanosDbContext db)
{
    public static double Peso(NivelDano nivel) => nivel switch
    {
        NivelDano.SIN_DANO => 0,
        NivelDano.LEVE => 0.25,
        NivelDano.MODERADO => 0.5,
        NivelDano.GRAVE => 0.75,
        NivelDano.DESTRUCCION_TOTAL => 1,
        _ => 0
    };

    public async Task<MapaCalorDto?> ObtenerAsync(Guid desastreId, NivelDano nivelMinimo)
    {
        bool existe = await (from d in db.Desastres where d.Id == desastreId select d.Id).AnyAsync();
        if (!existe)
        {
            return null;
        }

        // Última versión aplicada de cada activo que tenga evaluación.
        var ultimas =
            from v in db.ActivoVersiones
            where v.EvaluacionId != null
            group v by v.ActivoId into g
            select new { ActivoId = g.Key, Numero = g.Max(x => x.Numero) };

        var vigentes = await (
            from u in ultimas
            join v in db.ActivoVersiones on new { u.ActivoId, u.Numero } equals new { v.ActivoId, v.Numero }
            join e in db.Evaluaciones on v.EvaluacionId equals e.Id
            join a in db.Activos on u.ActivoId equals a.Id
            where e.DesastreId == desastreId
            select new { a.Id, a.TipoActivoCodigo, a.Lat, a.Lon, e.NivelDano }).ToListAsync();

        var activosEnConflicto = await (
            from c in db.Conflictos
            where c.Estado == EstadoConflicto.PENDIENTE
            select c.ActivoId).Distinct().ToListAsync();
        var enConflicto = activosEnConflicto.ToHashSet();

        var conteo = new Dictionary<string, int>();
        foreach (var n in Enum.GetValues<NivelDano>())
        {
            conteo[n.ToString()] = 0;
        }

        var puntos = new List<PuntoCalorDto>();
        foreach (var x in vigentes)
        {
            if (x.NivelDano < nivelMinimo)
            {
                continue;
            }
            conteo[x.NivelDano.ToString()]++;
            puntos.Add(new PuntoCalorDto(x.Id, x.TipoActivoCodigo, x.Lat, x.Lon, x.NivelDano, Peso(x.NivelDano), enConflicto.Contains(x.Id)));
        }

        var instanteDato = await (
            from e in db.Evaluaciones
            where e.DesastreId == desastreId
            select (DateTime?)e.InstanteServidor).MaxAsync();

        int pendientes = await (
            from c in db.Conflictos
            join e in db.Evaluaciones on c.EvaluacionEntranteId equals e.Id
            where c.Estado == EstadoConflicto.PENDIENTE && e.DesastreId == desastreId
            select c.Id).CountAsync();

        return new MapaCalorDto(desastreId, nivelMinimo, instanteDato, DateTime.UtcNow, pendientes, conteo, puntos);
    }
}
