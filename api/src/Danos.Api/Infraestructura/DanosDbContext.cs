using Danos.Api.Dominio;
using Microsoft.EntityFrameworkCore;

namespace Danos.Api.Infraestructura;

// El DbContext es el EntityManager de JPA: sigue los cambios y los guarda con SaveChanges.
// El mapeo de cada entidad está en ConfiguracionesEntidades.cs (Fluent API).
public class DanosDbContext(DbContextOptions<DanosDbContext> opciones) : DbContext(opciones)
{
    public DbSet<TipoActivo> TiposActivo => Set<TipoActivo>();
    public DbSet<CriterioNivel> CriteriosNivel => Set<CriterioNivel>();
    public DbSet<Desastre> Desastres => Set<Desastre>();
    public DbSet<Activo> Activos => Set<Activo>();
    public DbSet<ActivoVersion> ActivoVersiones => Set<ActivoVersion>();
    public DbSet<Evaluacion> Evaluaciones => Set<Evaluacion>();
    public DbSet<EvaluacionVersion> EvaluacionVersiones => Set<EvaluacionVersion>();
    public DbSet<Evidencia> Evidencias => Set<Evidencia>();
    public DbSet<Conflicto> Conflictos => Set<Conflicto>();
    public DbSet<OperacionProcesada> OperacionesProcesadas => Set<OperacionProcesada>();
    public DbSet<EventoSaliente> EventosSalientes => Set<EventoSaliente>();

    protected override void OnModelCreating(ModelBuilder modelo)
    {
        modelo.ApplyConfigurationsFromAssembly(typeof(DanosDbContext).Assembly);
    }

    public override int SaveChanges(bool acceptAllChangesOnSuccess)
    {
        RechazarCambiosAlHistorial();
        return base.SaveChanges(acceptAllChangesOnSuccess);
    }

    public override Task<int> SaveChangesAsync(bool acceptAllChangesOnSuccess, CancellationToken cancellationToken = default)
    {
        RechazarCambiosAlHistorial();
        return base.SaveChangesAsync(acceptAllChangesOnSuccess, cancellationToken);
    }

    /// <summary>EvaluacionVersion es de solo inserción: ninguna rama puede reescribir ni borrar el historial.</summary>
    private void RechazarCambiosAlHistorial()
    {
        foreach (var entrada in ChangeTracker.Entries<EvaluacionVersion>())
        {
            if (entrada.State == EntityState.Modified || entrada.State == EntityState.Deleted)
            {
                throw new HistorialInmutableException(
                    $"EvaluacionVersion {entrada.Entity.Id} es de solo inserción: no se permite {entrada.State}.");
            }
        }
    }
}

public class HistorialInmutableException(string mensaje) : InvalidOperationException(mensaje);
