using System.Security.Cryptography;
using Microsoft.Extensions.Options;

namespace Danos.Api.Infraestructura;

/// <summary>
/// Fotos en una carpeta del servidor; la base guarda solo ruta y hash. Se escribe primero a un temporal
/// (calculando el SHA-256 mientras se copia) y solo se mueve al destino final si el hash coincide.
/// </summary>
public class AlmacenArchivos(IOptions<OpcionesEvidencias> opciones, IHostEnvironment entorno)
{
    private string Raiz => Path.GetFullPath(Path.Combine(entorno.ContentRootPath, opciones.Value.Ruta));

    public string RutaFinal(Guid evaluacionId, Guid evidenciaId) =>
        Path.Combine(Raiz, evaluacionId.ToString(), evidenciaId + ".jpg");

    /// <summary>Copia el contenido a un temporal y devuelve su ruta, su SHA-256 (hex en minúsculas) y su tamaño.</summary>
    public async Task<ArchivoTemporal> GuardarTemporalAsync(Stream contenido, CancellationToken cancelar)
    {
        var carpeta = Path.Combine(Raiz, ".tmp");
        Directory.CreateDirectory(carpeta);
        var ruta = Path.Combine(carpeta, Guid.NewGuid().ToString("N") + ".part");

        using var sha = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
        long total = 0;
        await using (var destino = new FileStream(ruta, FileMode.CreateNew, FileAccess.Write, FileShare.None, 81920, useAsync: true))
        {
            var bufer = new byte[81920];
            int leidos;
            while ((leidos = await contenido.ReadAsync(bufer, cancelar)) > 0)
            {
                sha.AppendData(bufer, 0, leidos);
                await destino.WriteAsync(bufer.AsMemory(0, leidos), cancelar);
                total += leidos;
            }
        }
        return new ArchivoTemporal(ruta, Convert.ToHexStringLower(sha.GetHashAndReset()), total);
    }

    public void MoverAFinal(ArchivoTemporal temporal, string rutaFinal)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(rutaFinal)!);
        File.Move(temporal.Ruta, rutaFinal, overwrite: true);
    }

    public void Descartar(ArchivoTemporal temporal)
    {
        if (File.Exists(temporal.Ruta))
        {
            File.Delete(temporal.Ruta);
        }
    }
}

public sealed record ArchivoTemporal(string Ruta, string Sha256, long Bytes);
