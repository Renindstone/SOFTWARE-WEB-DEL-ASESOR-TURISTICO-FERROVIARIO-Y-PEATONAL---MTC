// Dibuja, sobre Leaflet.js, la estacion de origen, la zona turistica de
// destino y el trazo de la ruta peatonal (RF-04/RF-05, seccion 5.1).
//
// Consulta el motor de enrutamiento peatonal de OpenStreetMap (OSRM) para
// seguir las calles y esquinas reales (solo ida, un unico camino). Si OSRM
// no responde o no hay conexion, utiliza el trazo directo en linea recta
// (Haversine) como respaldo, para que el mapa nunca quede vacio.
//
// Las coordenadas llegan como data-attributes del contenedor del mapa, que la
// vista cliente/ruta-detalle.html rellena desde Estacion y ZonaTuristica.
document.addEventListener("DOMContentLoaded", function () {
  var contenedor = document.querySelector("[id^='mapa-']");
  if (!contenedor || typeof L === "undefined") {
    return;
  }

  var latOrigen = parseFloat(contenedor.dataset.latOrigen);
  var lonOrigen = parseFloat(contenedor.dataset.lonOrigen);
  if (isNaN(latOrigen) || isNaN(lonOrigen)) {
    return;
  }

  var mapa = L.map(contenedor.id).setView([latOrigen, lonOrigen], 14);

  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: "&copy; OpenStreetMap contributors"
  }).addTo(mapa);

  var nombreOrigen = contenedor.dataset.nombreOrigen || "Estación de origen";
  var marcadorOrigen = L.marker([latOrigen, lonOrigen]).addTo(mapa);
  marcadorOrigen.bindPopup("<strong>Inicio y retorno</strong><br>" + nombreOrigen);

  var latDestino = parseFloat(contenedor.dataset.latDestino);
  var lonDestino = parseFloat(contenedor.dataset.lonDestino);
  if (isNaN(latDestino) || isNaN(lonDestino)) {
    marcadorOrigen.openPopup();
    return;
  }

  var nombreDestino = contenedor.dataset.nombreDestino || "Zona turística";
  L.marker([latDestino, lonDestino]).addTo(mapa)
    .bindPopup("<strong>Destino</strong><br>" + nombreDestino);

  // ---- Respaldo: trazo directo (Haversine) ----
  function dibujarTrazoDirecto() {
    L.polyline([
      [latOrigen, lonOrigen],
      [latDestino, lonDestino]
    ], { color: "#14453D", weight: 4, opacity: 0.85, dashArray: "8, 6" }).addTo(mapa);

    mapa.fitBounds(L.latLngBounds([
      [latOrigen, lonOrigen],
      [latDestino, lonDestino]
    ]).pad(0.35));
  }

  // ---- OSRM: ruta peatonal por calles (solo ida, un unico camino) ----
  // Formato OSRM: lon,lat (invertido respecto a Leaflet)
  var urlOsrm = "https://router.project-osrm.org/route/v1/foot/"
    + lonOrigen + "," + latOrigen + ";"
    + lonDestino + "," + latDestino
    + "?overview=full&geometries=geojson&alternatives=false";

  // Timeout de 4.5s: si no hay respuesta, se usa el trazo directo.
  var abortCtrl = typeof AbortController !== "undefined" ? new AbortController() : null;
  var timerId = abortCtrl ? setTimeout(function () { abortCtrl.abort(); }, 4500) : null;

  fetch(urlOsrm, { signal: abortCtrl ? abortCtrl.signal : undefined })
    .then(function (res) {
      if (timerId) clearTimeout(timerId);
      if (!res.ok) throw new Error("OSRM status " + res.status);
      return res.json();
    })
    .then(function (data) {
      if (!data || data.code !== "Ok" || !data.routes || data.routes.length === 0
          || !data.routes[0].geometry) {
        dibujarTrazoDirecto();
        return;
      }

      // GeoJSON devuelve [lon, lat]; Leaflet necesita [lat, lon]
      var coords = data.routes[0].geometry.coordinates;
      var latLngs = coords.map(function (c) { return [c[1], c[0]]; });

      // OSRM "ajusta" (snap) las coordenadas al camino transitable mas
      // cercano. Para que la linea arranque y termine exactamente en los
      // marcadores, forzamos el primer y ultimo punto.
      latLngs[0] = [latOrigen, lonOrigen];
      latLngs[latLngs.length - 1] = [latDestino, lonDestino];

      // Ruta unica por calles
      var polyCalles = L.polyline(latLngs, {
        color: "#14453D",
        weight: 5,
        opacity: 0.9
      }).addTo(mapa);

      mapa.fitBounds(polyCalles.getBounds().pad(0.25));

      // Actualizar el texto debajo del mapa con la distancia real por calles
      var textoRuta = document.getElementById("texto-descripcion-ruta");
      if (textoRuta && data.routes[0].distance) {
        var kmCalles = (data.routes[0].distance / 1000).toFixed(2);
        textoRuta.innerHTML = '<i class="bi bi-signpost-2-fill text-success me-1"></i>'
          + ' Trazado peatonal por calles (ida: <strong>'
          + kmCalles + ' km</strong> v\u00eda OpenStreetMap).';
      }
    })
    .catch(function (err) {
      if (timerId) clearTimeout(timerId);
      console.info("Ruta peatonal OSRM no disponible, usando trazo directo:", err.message);
      dibujarTrazoDirecto();
    });
});
