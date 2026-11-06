# API de Evaluación de Daños: compila con el SDK y corre sobre el runtime de ASP.NET.
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS compilacion
WORKDIR /src
COPY src/Danos.Api/Danos.Api.csproj src/Danos.Api/
RUN dotnet restore src/Danos.Api/Danos.Api.csproj
COPY src/Danos.Api/ src/Danos.Api/
RUN dotnet publish src/Danos.Api/Danos.Api.csproj -c Release -o /app --no-restore

FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=compilacion /app .
# Development: al arrancar crea la base, aplica la migración y siembra tipos de activo y desastres.
ENV ASPNETCORE_ENVIRONMENT=Development
EXPOSE 5080
ENTRYPOINT ["dotnet", "Danos.Api.dll"]
