using System.Text.Json;
using Danos.Api.Acceso;
using Danos.Api.Infraestructura;
using Microsoft.EntityFrameworkCore;

namespace Danos.Api.Dominio;

/// <summary>Consultas para revisión humana y para verificar QA-14 (conflictos y evaluaciones).</summary>
public class ServicioRevision(DanosDbContext db)
{
    public async Task<List<ConflictoDto>> ConflictosAsync(EstadoConflicto estado)
    {
        var conflictos = await (
            from c in db.Conflictos.AsNoTracking()
            where c.Estado == estado
            orderby c.Instante
            select c).ToListAsync();

        var resultado = new List<ConflictoDto>();
        foreach (var c in conflictos)
        {
            var entrante = await (
                from v in db.EvaluacionVersiones.AsNoTracking()
                where v.Id == c.VersionEntranteId
                select v).SingleAsync();

            // La versión vigente es la última NO en conflicto de la evaluación vigente.
            EvaluacionVersion? vigente = null;
            if (c.EvaluacionVigenteId is Guid vigenteId)
            {
                vigente = await (
                    from v in db.EvaluacionVersiones.AsNoTracking()
                    where v.EvaluacionId == vigenteId && !v.EnConflicto
                    orderby v.NumeroVersion descending
                    select v).FirstOrDefaultAsync();
                vigente ??= await (
                    from v in db.EvaluacionVersiones.AsNoTracking()
                    where v.EvaluacionId == vigenteId
                    orderby v.NumeroVersion descending
                    select v).FirstOrDefaultAsync();
            }

            resultado.Add(new ConflictoDto(c.Id, c.ActivoId, c.Tipo, c.Estado, c.Regla, c.Instante,
                vigente is null ? null : ADto(vigente), ADto(entrante)));
        }
        return resultado;
    }

    public async Task<List<EvaluacionResumenDto>> EvaluacionesAsync(string idDispositivo)
    {
        var consulta =
            from e in db.Evaluaciones.AsNoTracking()
            where e.IdDispositivo == idDispositivo
            orderby e.InstanteServidor, e.Id
            select new EvaluacionResumenDto(
                e.Id, e.ActivoId, e.DesastreId, e.IdDispositivo, e.Estado, e.NivelDano,
                e.InstanteCampo, e.InstanteServidor,
                (from v in db.EvaluacionVersiones where v.EvaluacionId == e.Id select v).Count(),
                (from x in db.Evidencias where x.EvaluacionId == e.Id select x).Count(),
                (from x in db.Evidencias where x.EvaluacionId == e.Id && x.Subida select x).Count());
        return await consulta.ToListAsync();
    }

    private static VersionEvaluacionDto ADto(EvaluacionVersion v)
    {
        using var documento = JsonDocument.Parse(v.ContenidoJson);
        return new VersionEvaluacionDto(v.Id, v.EvaluacionId, v.NumeroVersion, v.Autor, v.IdDispositivo,
            v.VersionActivoBase, v.EnConflicto, v.Instante, documento.RootElement.Clone());
    }
}
