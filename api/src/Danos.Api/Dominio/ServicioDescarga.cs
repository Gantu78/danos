using Danos.Api.Acceso;
using Danos.Api.Infraestructura;
using Microsoft.EntityFrameworkCore;

namespace Danos.Api.Dominio;

/// <summary>Paquete de descarga previa: catálogo con criterios, desastres y activos con su versión.</summary>
public class ServicioDescarga(DanosDbContext db)
{
    public async Task<DescargaDto> ObtenerAsync(Guid? desastreId)
    {
        var instante = DateTime.UtcNow;

        var tipos = await (
            from t in db.TiposActivo.AsNoTracking().Include(t => t.Criterios)
            orderby t.Nombre
            select t).ToListAsync();

        var tiposDto = new List<TipoActivoDto>();
        foreach (var t in tipos)
        {
            var criterios = new List<CriterioDto>();
            foreach (var c in from c in t.Criterios orderby c.Nivel select c)
            {
                criterios.Add(new CriterioDto(c.Nivel, c.Criterio));
            }
            tiposDto.Add(new TipoActivoDto(t.Codigo, t.Nombre, criterios));
        }

        // Con desastreId solo se manda ese desastre; los activos son los mismos para todos.
        var consultaDesastres =
            from d in db.Desastres.AsNoTracking()
            where desastreId == null || d.Id == desastreId
            orderby d.Nombre
            select new DesastreDto(d.Id, d.Nombre, d.Tipo);

        var consultaActivos =
            from a in db.Activos.AsNoTracking()
            orderby a.Referencia
            select new ActivoDto(a.Id, a.TipoActivoCodigo, a.Lat, a.Lon, a.Referencia, a.Version);

        return new DescargaDto(instante, tiposDto, await consultaDesastres.ToListAsync(), await consultaActivos.ToListAsync());
    }
}
