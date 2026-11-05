using System;
using Microsoft.EntityFrameworkCore.Migrations;
using Npgsql.EntityFrameworkCore.PostgreSQL.Metadata;

#nullable disable

namespace Danos.Api.Infraestructura.Migraciones
{
    /// <inheritdoc />
    public partial class Inicial : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "desastre",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    Nombre = table.Column<string>(type: "character varying(150)", maxLength: 150, nullable: false),
                    Tipo = table.Column<string>(type: "character varying(40)", maxLength: 40, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_desastre", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "evento_saliente",
                columns: table => new
                {
                    Id = table.Column<long>(type: "bigint", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    Tipo = table.Column<string>(type: "character varying(10)", maxLength: 10, nullable: false),
                    PayloadJson = table.Column<string>(type: "jsonb", nullable: false),
                    Instante = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    Publicado = table.Column<bool>(type: "boolean", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_evento_saliente", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "operacion_procesada",
                columns: table => new
                {
                    IdOperacion = table.Column<Guid>(type: "uuid", nullable: false),
                    IdDispositivo = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    ResultadoJson = table.Column<string>(type: "jsonb", nullable: false),
                    Instante = table.Column<DateTime>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_operacion_procesada", x => x.IdOperacion);
                });

            migrationBuilder.CreateTable(
                name: "tipo_activo",
                columns: table => new
                {
                    Codigo = table.Column<string>(type: "character varying(40)", maxLength: 40, nullable: false),
                    Nombre = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_tipo_activo", x => x.Codigo);
                });

            migrationBuilder.CreateTable(
                name: "activo",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    TipoActivoCodigo = table.Column<string>(type: "character varying(40)", maxLength: 40, nullable: false),
                    Lat = table.Column<double>(type: "double precision", nullable: false),
                    Lon = table.Column<double>(type: "double precision", nullable: false),
                    Referencia = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    Version = table.Column<int>(type: "integer", nullable: false),
                    CreadoPorDispositivo = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_activo", x => x.Id);
                    table.ForeignKey(
                        name: "FK_activo_tipo_activo_TipoActivoCodigo",
                        column: x => x.TipoActivoCodigo,
                        principalTable: "tipo_activo",
                        principalColumn: "Codigo",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "criterio_nivel",
                columns: table => new
                {
                    TipoActivoCodigo = table.Column<string>(type: "character varying(40)", nullable: false),
                    Nivel = table.Column<string>(type: "character varying(20)", maxLength: 20, nullable: false),
                    Criterio = table.Column<string>(type: "character varying(300)", maxLength: 300, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_criterio_nivel", x => new { x.TipoActivoCodigo, x.Nivel });
                    table.ForeignKey(
                        name: "FK_criterio_nivel_tipo_activo_TipoActivoCodigo",
                        column: x => x.TipoActivoCodigo,
                        principalTable: "tipo_activo",
                        principalColumn: "Codigo",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "activo_version",
                columns: table => new
                {
                    ActivoId = table.Column<Guid>(type: "uuid", nullable: false),
                    Numero = table.Column<int>(type: "integer", nullable: false),
                    IdDispositivo = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    EvaluacionId = table.Column<Guid>(type: "uuid", nullable: true),
                    Instante = table.Column<DateTime>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_activo_version", x => new { x.ActivoId, x.Numero });
                    table.ForeignKey(
                        name: "FK_activo_version_activo_ActivoId",
                        column: x => x.ActivoId,
                        principalTable: "activo",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "evaluacion",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    DesastreId = table.Column<Guid>(type: "uuid", nullable: true),
                    InstanteCampo = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    Evaluador = table.Column<string>(type: "character varying(150)", maxLength: 150, nullable: false),
                    Organizacion = table.Column<string>(type: "character varying(150)", maxLength: 150, nullable: false),
                    Lat = table.Column<double>(type: "double precision", nullable: false),
                    Lon = table.Column<double>(type: "double precision", nullable: false),
                    TipoActivoCodigo = table.Column<string>(type: "character varying(40)", maxLength: 40, nullable: false),
                    NivelDano = table.Column<string>(type: "character varying(20)", maxLength: 20, nullable: false),
                    Descripcion = table.Column<string>(type: "character varying(2000)", maxLength: 2000, nullable: false),
                    Estado = table.Column<string>(type: "character varying(20)", maxLength: 20, nullable: false),
                    ActivoId = table.Column<Guid>(type: "uuid", nullable: false),
                    PrecisionM = table.Column<double>(type: "double precision", nullable: false),
                    InstanteServidor = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    IdDispositivo = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    DesfaseRelojMs = table.Column<long>(type: "bigint", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_evaluacion", x => x.Id);
                    table.ForeignKey(
                        name: "FK_evaluacion_activo_ActivoId",
                        column: x => x.ActivoId,
                        principalTable: "activo",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "FK_evaluacion_desastre_DesastreId",
                        column: x => x.DesastreId,
                        principalTable: "desastre",
                        principalColumn: "Id");
                    table.ForeignKey(
                        name: "FK_evaluacion_tipo_activo_TipoActivoCodigo",
                        column: x => x.TipoActivoCodigo,
                        principalTable: "tipo_activo",
                        principalColumn: "Codigo",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "evaluacion_version",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    EvaluacionId = table.Column<Guid>(type: "uuid", nullable: false),
                    NumeroVersion = table.Column<int>(type: "integer", nullable: false),
                    ContenidoJson = table.Column<string>(type: "jsonb", nullable: false),
                    Autor = table.Column<string>(type: "character varying(150)", maxLength: 150, nullable: false),
                    IdDispositivo = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    VersionActivoBase = table.Column<int>(type: "integer", nullable: true),
                    EnConflicto = table.Column<bool>(type: "boolean", nullable: false),
                    Instante = table.Column<DateTime>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_evaluacion_version", x => x.Id);
                    table.ForeignKey(
                        name: "FK_evaluacion_version_evaluacion_EvaluacionId",
                        column: x => x.EvaluacionId,
                        principalTable: "evaluacion",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "evidencia",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    EvaluacionId = table.Column<Guid>(type: "uuid", nullable: false),
                    Lat = table.Column<double>(type: "double precision", nullable: false),
                    Lon = table.Column<double>(type: "double precision", nullable: false),
                    InstanteCaptura = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    Sha256Declarado = table.Column<string>(type: "character varying(64)", maxLength: 64, nullable: false),
                    TamanoBytes = table.Column<long>(type: "bigint", nullable: false),
                    Subida = table.Column<bool>(type: "boolean", nullable: false),
                    RutaArchivo = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: true),
                    InstanteSubida = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_evidencia", x => x.Id);
                    table.ForeignKey(
                        name: "FK_evidencia_evaluacion_EvaluacionId",
                        column: x => x.EvaluacionId,
                        principalTable: "evaluacion",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "conflicto",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    ActivoId = table.Column<Guid>(type: "uuid", nullable: false),
                    Tipo = table.Column<string>(type: "character varying(20)", maxLength: 20, nullable: false),
                    EvaluacionVigenteId = table.Column<Guid>(type: "uuid", nullable: true),
                    EvaluacionEntranteId = table.Column<Guid>(type: "uuid", nullable: false),
                    VersionEntranteId = table.Column<Guid>(type: "uuid", nullable: false),
                    Regla = table.Column<string>(type: "text", nullable: false),
                    Estado = table.Column<string>(type: "character varying(20)", maxLength: 20, nullable: false),
                    Instante = table.Column<DateTime>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_conflicto", x => x.Id);
                    table.ForeignKey(
                        name: "FK_conflicto_activo_ActivoId",
                        column: x => x.ActivoId,
                        principalTable: "activo",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "FK_conflicto_evaluacion_EvaluacionEntranteId",
                        column: x => x.EvaluacionEntranteId,
                        principalTable: "evaluacion",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "FK_conflicto_evaluacion_EvaluacionVigenteId",
                        column: x => x.EvaluacionVigenteId,
                        principalTable: "evaluacion",
                        principalColumn: "Id");
                    table.ForeignKey(
                        name: "FK_conflicto_evaluacion_version_VersionEntranteId",
                        column: x => x.VersionEntranteId,
                        principalTable: "evaluacion_version",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_activo_TipoActivoCodigo_Lat_Lon",
                table: "activo",
                columns: new[] { "TipoActivoCodigo", "Lat", "Lon" });

            migrationBuilder.CreateIndex(
                name: "IX_conflicto_ActivoId",
                table: "conflicto",
                column: "ActivoId");

            migrationBuilder.CreateIndex(
                name: "IX_conflicto_Estado",
                table: "conflicto",
                column: "Estado");

            migrationBuilder.CreateIndex(
                name: "IX_conflicto_EvaluacionEntranteId",
                table: "conflicto",
                column: "EvaluacionEntranteId");

            migrationBuilder.CreateIndex(
                name: "IX_conflicto_EvaluacionVigenteId",
                table: "conflicto",
                column: "EvaluacionVigenteId");

            migrationBuilder.CreateIndex(
                name: "IX_conflicto_VersionEntranteId",
                table: "conflicto",
                column: "VersionEntranteId");

            migrationBuilder.CreateIndex(
                name: "IX_evaluacion_ActivoId",
                table: "evaluacion",
                column: "ActivoId");

            migrationBuilder.CreateIndex(
                name: "IX_evaluacion_DesastreId",
                table: "evaluacion",
                column: "DesastreId");

            migrationBuilder.CreateIndex(
                name: "IX_evaluacion_IdDispositivo",
                table: "evaluacion",
                column: "IdDispositivo");

            migrationBuilder.CreateIndex(
                name: "IX_evaluacion_TipoActivoCodigo",
                table: "evaluacion",
                column: "TipoActivoCodigo");

            migrationBuilder.CreateIndex(
                name: "IX_evaluacion_version_EvaluacionId_NumeroVersion",
                table: "evaluacion_version",
                columns: new[] { "EvaluacionId", "NumeroVersion" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_evento_saliente_Publicado",
                table: "evento_saliente",
                column: "Publicado");

            migrationBuilder.CreateIndex(
                name: "IX_evidencia_EvaluacionId",
                table: "evidencia",
                column: "EvaluacionId");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "activo_version");

            migrationBuilder.DropTable(
                name: "conflicto");

            migrationBuilder.DropTable(
                name: "criterio_nivel");

            migrationBuilder.DropTable(
                name: "evento_saliente");

            migrationBuilder.DropTable(
                name: "evidencia");

            migrationBuilder.DropTable(
                name: "operacion_procesada");

            migrationBuilder.DropTable(
                name: "evaluacion_version");

            migrationBuilder.DropTable(
                name: "evaluacion");

            migrationBuilder.DropTable(
                name: "activo");

            migrationBuilder.DropTable(
                name: "desastre");

            migrationBuilder.DropTable(
                name: "tipo_activo");
        }
    }
}
