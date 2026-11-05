// Mapa de calor con Leaflet + Leaflet.heat. JavaScript plano, como mapa.js del taller.
// Pide /v1/mapa-calor al propio API cada 10 s y REEMPLAZA las capas (no redibuja el mapa).
// El API exige los encabezados X-*: la consola se identifica como un dispositivo más.

var COLORES = {
    SIN_DANO: "#2e9e44", LEVE: "#e8c21a", MODERADO: "#ef7d22", GRAVE: "#d62f2f", DESTRUCCION_TOTAL: "#5b1414"
};
var ETIQUETAS = {
    SIN_DANO: "Sin daño", LEVE: "Leve", MODERADO: "Moderado", GRAVE: "Grave", DESTRUCCION_TOTAL: "Destrucción total"
};
var CENTRO_BOGOTA = [4.63, -74.11];
var CADA_MS = 10000;
var ENCABEZADOS = {
    "Accept": "application/json",
    "X-Usuario": "consola",
    "X-Organizacion": "consola",
    "X-Dispositivo": "consola-mapa"
};

var contenedor = document.getElementById("mapa");
var url = contenedor.dataset.url;
var estadoVivo = document.getElementById("estado-vivo");
var horaDato = document.getElementById("hora-dato");
var conflictos = document.getElementById("conflictos");
var verPuntos = document.getElementById("ver-puntos");

var mapa = L.map(contenedor, { zoomControl: true }).setView(CENTRO_BOGOTA, 12);
L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
}).addTo(mapa);

// La capa de calor se agrega al mapa solo cuando este ya tiene tamaño: Leaflet.heat falla
// (getImageData con ancho 0) si se agrega mientras el contenedor todavía mide 0 px.
// maxZoom bajo a propósito: Leaflet.heat divide la intensidad entre 2 por cada nivel de zoom que falta
// para llegar a maxZoom. Con 16, a escala de ciudad (zoom 12-13) cada punto pintaba con 1/8-1/16 de su
// peso y la capa quedaba casi transparente.
var capaCalor = L.heatLayer([], {
    radius: 30, blur: 20, maxZoom: 10, max: 1,
    gradient: { 0.25: "#e8c21a", 0.5: "#ef7d22", 0.75: "#d62f2f", 1: "#5b1414" }
});

function asegurarCapaCalor() {
    mapa.invalidateSize();
    var tamano = mapa.getSize();
    if (!mapa.hasLayer(capaCalor) && tamano.x > 0 && tamano.y > 0) {
        capaCalor.addTo(mapa);
    }
}
window.addEventListener("load", asegurarCapaCalor);
window.addEventListener("resize", asegurarCapaCalor);
var capaPuntos = L.layerGroup().addTo(mapa);
var vistaAjustada = false;

function crearPopup(p) {
    // textContent y no HTML: nada que venga del servidor se interpreta como marcado.
    var caja = document.createElement("div");
    caja.className = "popup";
    var titulo = document.createElement("strong");
    titulo.textContent = (ETIQUETAS[p.nivel] || p.nivel) + " · " + p.tipoActivoCodigo;
    caja.appendChild(titulo);
    if (p.enConflicto) {
        var aviso = document.createElement("p");
        aviso.className = "popup-alerta";
        aviso.textContent = "Conflicto pendiente de revisión: se muestra la evaluación vigente.";
        caja.appendChild(aviso);
    }
    return caja;
}

function dibujar(datos) {
    var calor = [];
    var limites = [];
    capaPuntos.clearLayers();

    for (var i = 0; i < datos.puntos.length; i++) {
        var p = datos.puntos[i];
        limites.push([p.lat, p.lon]);
        if (p.peso > 0) {
            calor.push([p.lat, p.lon, p.peso]);   // sin daño (peso 0) no calienta el mapa
        }
        var marcador = L.circleMarker([p.lat, p.lon], {
            radius: 6,
            color: p.enConflicto ? "#9b2c1f" : "#1c2b36",
            weight: p.enConflicto ? 3 : 1,
            dashArray: p.enConflicto ? "3 3" : null,
            fillColor: COLORES[p.nivel] || "#888",
            fillOpacity: 0.9
        });
        marcador.bindPopup(crearPopup(p));
        capaPuntos.addLayer(marcador);
    }
    asegurarCapaCalor();
    capaCalor.setLatLngs(calor);

    if (!vistaAjustada && limites.length > 0) {
        mapa.fitBounds(limites, { padding: [40, 40], maxZoom: 15 });
        vistaAjustada = true;
    }

    for (var nivel in datos.conteoPorNivel) {
        var celda = document.querySelector('.nivel[data-nivel="' + nivel + '"] .cuenta');
        if (celda) {
            celda.textContent = datos.conteoPorNivel[nivel];
        }
    }
    conflictos.textContent = datos.conflictosPendientes;
    // AD-16: siempre la marca de tiempo del dato (hora de Colombia).
    horaDato.textContent = datos.instanteDato
        ? new Date(datos.instanteDato).toLocaleTimeString("es-CO", { timeZone: "America/Bogota", hour12: false })
        : "sin datos";
    estadoVivo.textContent = datos.puntos.length + " activos evaluados. Se actualiza cada 10 segundos.";
}

function pedir() {
    fetch(url, { headers: ENCABEZADOS })
        .then(function (respuesta) {
            return respuesta.json().then(function (cuerpo) {
                if (!respuesta.ok) {
                    throw new Error(cuerpo.error || ("Error " + respuesta.status));
                }
                return cuerpo;
            });
        })
        .then(function (datos) {
            estadoVivo.classList.remove("vivo-error");
            dibujar(datos);
        })
        .catch(function (error) {
            // Se deja dibujado lo último que se tenía y se avisa.
            estadoVivo.classList.add("vivo-error");
            estadoVivo.textContent = "No se pudo actualizar el mapa: " + error.message;
        });
}

verPuntos.addEventListener("change", function () {
    if (verPuntos.checked) {
        capaPuntos.addTo(mapa);
    } else {
        mapa.removeLayer(capaPuntos);
    }
});

pedir();
setInterval(pedir, CADA_MS);
