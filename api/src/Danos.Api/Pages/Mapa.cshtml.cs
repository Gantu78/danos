using Danos.Api.Acceso;
using Danos.Api.Infraestructura;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.RazorPages;
using Microsoft.EntityFrameworkCore;

namespace Danos.Api.Pages;

// Página /mapa?desastreId=..&nivelMinimo=.. con el mapa de calor. No es parte del API REST: es la
// interfaz de la consola. Los puntos los pide calor.js cada 10 s a /v1/mapa-calor del propio API.
public class MapaModel(DanosDbContext db) : PageModel
{
    public List<DesastreDto> Desastres { get; private set; } = [];

    [BindProperty(SupportsGet = true)]
    public Guid? DesastreId { get; set; }

    [BindProperty(SupportsGet = true)]
    public string NivelMinimo { get; set; } = "SIN_DANO";

    public DesastreDto? Desastre { get; private set; }

    public async Task OnGetAsync()
    {
        Desastres = await (
            from d in db.Desastres.AsNoTracking()
            orderby d.Nombre
            select new DesastreDto(d.Id, d.Nombre, d.Tipo)).ToListAsync();

        Desastre = Desastres.FirstOrDefault(d => d.Id == DesastreId) ?? Desastres.FirstOrDefault();
        DesastreId = Desastre?.Id;
        if (!Dominio.ValidadorEvaluacion.EsNombreDeEnum<Dominio.NivelDano>(NivelMinimo))
        {
            NivelMinimo = "SIN_DANO";
        }
    }

    public static string Etiqueta(Dominio.NivelDano nivel) => nivel switch
    {
        Dominio.NivelDano.SIN_DANO => "Sin daño",
        Dominio.NivelDano.LEVE => "Leve",
        Dominio.NivelDano.MODERADO => "Moderado",
        Dominio.NivelDano.GRAVE => "Grave",
        Dominio.NivelDano.DESTRUCCION_TOTAL => "Destrucción total",
        _ => nivel.ToString()
    };
}
