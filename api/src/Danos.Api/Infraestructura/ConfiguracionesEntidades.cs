using Danos.Api.Dominio;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace Danos.Api.Infraestructura;

// Fluent API: lo que en JPA serían @Table, @Column, @Enumerated, @Version.
// Los enums se guardan como texto ("GRAVE") y no como número.

public class ConfiguracionTipoActivo : IEntityTypeConfiguration<TipoActivo>
{
    public void Configure(EntityTypeBuilder<TipoActivo> e)
    {
        e.ToTable("tipo_activo");
        e.HasKey(x => x.Codigo);
        e.Property(x => x.Codigo).HasMaxLength(Limites.CodigoTipo);
        e.Property(x => x.Nombre).IsRequired().HasMaxLength(100);
        e.HasMany(x => x.Criterios).WithOne().HasForeignKey(c => c.TipoActivoCodigo);
    }
}

public class ConfiguracionCriterioNivel : IEntityTypeConfiguration<CriterioNivel>
{
    public void Configure(EntityTypeBuilder<CriterioNivel> e)
    {
        e.ToTable("criterio_nivel");
        e.HasKey(x => new { x.TipoActivoCodigo, x.Nivel });
        e.Property(x => x.Nivel).HasConversion<string>().HasMaxLength(20);
        e.Property(x => x.Criterio).IsRequired().HasMaxLength(300);
    }
}

public class ConfiguracionDesastre : IEntityTypeConfiguration<Desastre>
{
    public void Configure(EntityTypeBuilder<Desastre> e)
    {
        e.ToTable("desastre");
        e.Property(x => x.Id).ValueGeneratedNever();
        e.Property(x => x.Nombre).IsRequired().HasMaxLength(150);
        e.Property(x => x.Tipo).IsRequired().HasMaxLength(40);
    }
}

public class ConfiguracionActivo : IEntityTypeConfiguration<Activo>
{
    public void Configure(EntityTypeBuilder<Activo> e)
    {
        e.ToTable("activo");
        e.Property(x => x.Id).ValueGeneratedNever();
        e.Property(x => x.TipoActivoCodigo).IsRequired().HasMaxLength(Limites.CodigoTipo);
        e.Property(x => x.Referencia).HasMaxLength(Limites.Referencia);
        e.Property(x => x.CreadoPorDispositivo).HasMaxLength(Limites.IdDispositivo);
        // Como @Version de JPA: el UPDATE lleva "WHERE version = <la leída>" y si otro lote la cambió
        // en paralelo, SaveChanges lanza DbUpdateConcurrencyException.
        e.Property(x => x.Version).IsConcurrencyToken();
        e.HasOne<TipoActivo>().WithMany().HasForeignKey(x => x.TipoActivoCodigo);
        // El detector de duplicados busca por tipo dentro de un recuadro de coordenadas.
        e.HasIndex(x => new { x.TipoActivoCodigo, x.Lat, x.Lon });
    }
}

public class ConfiguracionActivoVersion : IEntityTypeConfiguration<ActivoVersion>
{
    public void Configure(EntityTypeBuilder<ActivoVersion> e)
    {
        e.ToTable("activo_version");
        e.HasKey(x => new { x.ActivoId, x.Numero });
        e.Property(x => x.IdDispositivo).IsRequired().HasMaxLength(Limites.IdDispositivo);
        e.HasOne<Activo>().WithMany().HasForeignKey(x => x.ActivoId);
    }
}

public class ConfiguracionEvaluacion : IEntityTypeConfiguration<Evaluacion>
{
    public void Configure(EntityTypeBuilder<Evaluacion> e)
    {
        e.ToTable("evaluacion");
        e.Property(x => x.Id).ValueGeneratedNever();
        e.Property(x => x.Evaluador).IsRequired().HasMaxLength(Limites.Nombre);
        e.Property(x => x.Organizacion).IsRequired().HasMaxLength(Limites.Nombre);
        e.Property(x => x.TipoActivoCodigo).IsRequired().HasMaxLength(Limites.CodigoTipo);
        e.Property(x => x.NivelDano).HasConversion<string>().HasMaxLength(20);
        e.Property(x => x.Estado).HasConversion<string>().HasMaxLength(20);
        e.Property(x => x.Descripcion).HasMaxLength(Limites.Descripcion);
        e.Property(x => x.IdDispositivo).IsRequired().HasMaxLength(Limites.IdDispositivo);
        e.HasOne<Activo>().WithMany().HasForeignKey(x => x.ActivoId);
        e.HasOne<Desastre>().WithMany().HasForeignKey(x => x.DesastreId);
        e.HasOne<TipoActivo>().WithMany().HasForeignKey(x => x.TipoActivoCodigo);
        e.HasIndex(x => x.IdDispositivo);
        e.HasIndex(x => x.ActivoId);
    }
}

public class ConfiguracionEvaluacionVersion : IEntityTypeConfiguration<EvaluacionVersion>
{
    public void Configure(EntityTypeBuilder<EvaluacionVersion> e)
    {
        e.ToTable("evaluacion_version");
        e.Property(x => x.Id).ValueGeneratedNever();
        e.Property(x => x.ContenidoJson).HasColumnType("jsonb");
        e.Property(x => x.Autor).IsRequired().HasMaxLength(Limites.Nombre);
        e.Property(x => x.IdDispositivo).IsRequired().HasMaxLength(Limites.IdDispositivo);
        e.HasOne<Evaluacion>().WithMany().HasForeignKey(x => x.EvaluacionId);
        e.HasIndex(x => new { x.EvaluacionId, x.NumeroVersion }).IsUnique();
    }
}

public class ConfiguracionEvidencia : IEntityTypeConfiguration<Evidencia>
{
    public void Configure(EntityTypeBuilder<Evidencia> e)
    {
        e.ToTable("evidencia");
        e.Property(x => x.Id).ValueGeneratedNever();
        e.Property(x => x.Sha256Declarado).IsRequired().HasMaxLength(64);
        e.Property(x => x.RutaArchivo).HasMaxLength(500);
        e.HasOne<Evaluacion>().WithMany().HasForeignKey(x => x.EvaluacionId);
        e.HasIndex(x => x.EvaluacionId);
    }
}

public class ConfiguracionConflicto : IEntityTypeConfiguration<Conflicto>
{
    public void Configure(EntityTypeBuilder<Conflicto> e)
    {
        e.ToTable("conflicto");
        e.Property(x => x.Id).ValueGeneratedNever();
        e.Property(x => x.Tipo).HasConversion<string>().HasMaxLength(20);
        e.Property(x => x.Estado).HasConversion<string>().HasMaxLength(20);
        // text: la regla nombra a todos los dispositivos que cambiaron el activo y no tiene tope.
        e.Property(x => x.Regla).IsRequired().HasColumnType("text");
        e.HasOne<Activo>().WithMany().HasForeignKey(x => x.ActivoId);
        e.HasOne<Evaluacion>().WithMany().HasForeignKey(x => x.EvaluacionVigenteId);
        e.HasOne<Evaluacion>().WithMany().HasForeignKey(x => x.EvaluacionEntranteId);
        e.HasOne<EvaluacionVersion>().WithMany().HasForeignKey(x => x.VersionEntranteId);
        e.HasIndex(x => x.Estado);
    }
}

public class ConfiguracionOperacionProcesada : IEntityTypeConfiguration<OperacionProcesada>
{
    public void Configure(EntityTypeBuilder<OperacionProcesada> e)
    {
        e.ToTable("operacion_procesada");
        e.HasKey(x => x.IdOperacion);
        e.Property(x => x.IdOperacion).ValueGeneratedNever();
        e.Property(x => x.IdDispositivo).IsRequired().HasMaxLength(Limites.IdDispositivo);
        e.Property(x => x.ResultadoJson).HasColumnType("jsonb");
    }
}

public class ConfiguracionEventoSaliente : IEntityTypeConfiguration<EventoSaliente>
{
    public void Configure(EntityTypeBuilder<EventoSaliente> e)
    {
        e.ToTable("evento_saliente");
        e.Property(x => x.Tipo).IsRequired().HasMaxLength(10);
        e.Property(x => x.PayloadJson).HasColumnType("jsonb");
        e.HasIndex(x => x.Publicado);
    }
}
