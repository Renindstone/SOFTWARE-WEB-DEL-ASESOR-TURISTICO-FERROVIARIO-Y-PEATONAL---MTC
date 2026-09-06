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

  filas.forEach(function (fila, indice) {
    var lat = parseFloat(fila.dataset.lat);
    var lon = parseFloat(fila.dataset.lon);
    if (isNaN(lat) || isNaN(lon)) {
      return;
    }

    var marcador = marcadorDe(fila.dataset.dificultad, false)
      .setLatLng([lat, lon])
      .addTo(mapa);

    marcador.bindPopup(
      "<strong>" + (fila.dataset.nombre || "Zona turística") + "</strong><br>" +
      "Desde " + (fila.dataset.estacion || "su estación de acceso")
    );

    // Del mapa al tablero: pulsar un marcador lleva a su ficha.
    marcador.on("click", function () {
      filas.forEach(function (otra) { otra.classList.remove("is-activa"); });
      fila.classList.add("is-activa");
      fila.scrollIntoView({ block: "nearest", behavior: "smooth" });
    });

    // Del tablero al mapa. "focusin" ademas de "mouseenter" para que el
    // recorrido con teclado resalte lo mismo que el puntero.
    ["mouseenter", "focusin"].forEach(function (evento) {
      fila.addEventListener(evento, function () {
        marcador.setRadius(11);
        marcador.setStyle({ weight: 3 });
      });
    });

    ["mouseleave", "focusout"].forEach(function (evento) {
      fila.addEventListener(evento, function () {
        marcador.setRadius(7);
        marcador.setStyle({ weight: 2 });
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
