// Situa sobre Leaflet.js todas las zonas que devolvio el buscador (RF-03) y
// mantiene sincronizados el tablero y el mapa en los dos sentidos: al recorrer
// una fila se resalta su marcador, y al pulsar un marcador se enfoca su fila.
//
// Las coordenadas no son un dato nuevo: ZonaResultadoDTO ya expone latitud y
// longitud, y cliente/preferencias.html las vuelca en los data-attributes de
// cada fila del tablero. Por eso este archivo no obliga a tocar el backend.
//
// Es progresivo: sin JavaScript, o sin Leaflet, el tablero sigue siendo un
// listado completo y utilizable.
document.addEventListener("DOMContentLoaded", function () {
  var contenedor = document.getElementById("buscador-mapa");
  if (!contenedor || typeof L === "undefined") {
    return;
  }

  var filas = Array.prototype.slice.call(
    document.querySelectorAll(".tablero-fila[data-lat][data-lon]")
  );
  if (filas.length === 0) {
    contenedor.remove();
    return;
  }

  var mapa = L.map(contenedor, { scrollWheelZoom: false });

  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: "&copy; OpenStreetMap contributors"
  }).addTo(mapa);

  // El color del marcador codifica la dificultad del circuito, la misma
  // escala que usan las insignias del tablero.
  var COLOR_POR_DIFICULTAD = {
    "Baja": "#2E6B45",
    "Media": "#976316",
    "Alta": "#A8341F"
  };

  function marcadorDe(dificultad, resaltado) {
    return L.circleMarker([0, 0], {
      radius: resaltado ? 11 : 7,
      color: "#FFFFFF",
      weight: 2,
      fillColor: COLOR_POR_DIFICULTAD[dificultad] || "#14453D",
      fillOpacity: 1
    });
  }

  var puntos = [];
  var marcadores = [];
  var marcadorActivo = null;

  function seleccionarZona(fila, marcador, moverMapa) {
    // 1. Quitar estado activo anterior
    filas.forEach(function (otra) { otra.classList.remove("is-activa"); });
    if (marcadorActivo && marcadorActivo !== marcador) {
      marcadorActivo.setRadius(7);
      marcadorActivo.setStyle({ color: "#FFFFFF", weight: 2 });
    }

    // 2. Activar tarjeta actual y enfocarla
    fila.classList.add("is-activa");
    fila.scrollIntoView({ block: "center", behavior: "smooth" });

    // 3. Resaltar marcador en el mapa con borde dorado institucional
    marcadorActivo = marcador;
    marcador.setRadius(12);
    marcador.setStyle({ color: "#C89B3C", weight: 3.5 });
    marcador.bringToFront();

    // 4. Si la seleccion viene del tablero, centrar mapa y abrir popup
    if (moverMapa) {
      marcador.openPopup();
      mapa.panTo(marcador.getLatLng());
    }
  }

  filas.forEach(function (fila, indice) {
    var lat = parseFloat(fila.dataset.lat);
    var lon = parseFloat(fila.dataset.lon);
    if (isNaN(lat) || isNaN(lon)) {
      return;
    }

    var marcador = marcadorDe(fila.dataset.dificultad, false)
      .setLatLng([lat, lon])
      .addTo(mapa);

    var urlDetalle = fila.dataset.url;
    var popupHtml =
      "<div class='mapa-popup-contenido'>" +
        "<strong class='d-block mb-1'>" + (fila.dataset.nombre || "Zona turística") + "</strong>" +
        "<div class='text-muted small mb-2'><i class='bi bi-train-front me-1'></i>Desde " + (fila.dataset.estacion || "estación") + "</div>" +
        (urlDetalle ?
          "<a href='" + urlDetalle + "' class='btn btn-mtc btn-sm w-100 text-center py-1'><i class='bi bi-geo-alt-fill me-1'></i>Ver ruta y clima</a>"
          : "") +
      "</div>";

    marcador.bindPopup(popupHtml);

    // Del mapa al tablero: pulsar un marcador resalta su tarjeta y muestra donde interactuar
    marcador.on("click", function () {
      seleccionarZona(fila, marcador, false);
    });

    // Del tablero al mapa: pulsar la tarjeta enfoca el marcador correspondiente
    fila.addEventListener("click", function (evento) {
      if (evento.target.closest("a, button")) {
        return; // No interceptar clics sobre enlaces o botones directos
      }
      seleccionarZona(fila, marcador, true);
    });

    // Del tablero al mapa en hover/foco (sin alterar el marcador activo)
    ["mouseenter", "focusin"].forEach(function (evento) {
      fila.addEventListener(evento, function () {
        if (marcador !== marcadorActivo) {
          marcador.setRadius(10);
          marcador.setStyle({ weight: 3 });
        }
      });
    });

    ["mouseleave", "focusout"].forEach(function (evento) {
      fila.addEventListener(evento, function () {
        if (marcador !== marcadorActivo) {
          marcador.setRadius(7);
          marcador.setStyle({ weight: 2 });
        }
      });
    });

    puntos.push([lat, lon]);
    marcadores[indice] = marcador;
  });

  if (puntos.length === 0) {
    contenedor.remove();
    return;
  }

  if (puntos.length === 1) {
    mapa.setView(puntos[0], 14);
  } else {
    mapa.fitBounds(L.latLngBounds(puntos).pad(0.25));
  }

  // El zoom con rueda queda desactivado para que la pagina siga
  // desplazandose con normalidad; se activa al entrar al mapa a proposito.
  mapa.on("focus", function () { mapa.scrollWheelZoom.enable(); });
  mapa.on("blur", function () { mapa.scrollWheelZoom.disable(); });
});
