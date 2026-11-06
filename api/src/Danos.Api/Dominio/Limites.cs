namespace Danos.Api.Dominio;

/// <summary>Largos máximos de las columnas de texto. Los usan el validador y la configuración de EF.</summary>
public static class Limites
{
    public const int Nombre = 150;
    public const int Descripcion = 2000;
    public const int Referencia = 200;
    public const int IdDispositivo = 100;
    public const int CodigoTipo = 40;
}
