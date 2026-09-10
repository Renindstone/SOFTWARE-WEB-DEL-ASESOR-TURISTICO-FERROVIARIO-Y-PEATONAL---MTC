// Dibuja, sobre Leaflet.js, la estacion de origen, la zona turistica de
// destino y el trazo de la ruta peatonal (RF-04/RF-05, seccion 5.1).
//
// Consulta el motor de enrutamiento peatonal de OpenStreetMap (OSRM) para
// seguir las calles y esquinas reales (solo ida, un unico camino). Si OSRM
// no responde o no hay conexion, utiliza el trazo directo en linea recta
// (Haversine) como respaldo, para que el mapa nunca quede vacio.
//
// Cuando OSRM si responde, armonizarDistancias() pone de acuerdo la tarjeta
// superior con el mapa: de otro modo la pantalla muestra a la vez la distancia
// en linea recta y la del camino por calles, que nunca coinciden. Si OSRM
// falla, no se toca nada y las cifras de Haversine se quedan tal cual: es
// preferible una estimacion coherente a una mezcla de las dos.
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

  /**
   * Pone de acuerdo las tres cifras de distancia de la pantalla.
   *
   * La tarjeta superior muestra el circuito de ida y vuelta calculado con
   * Haversine, en linea recta, mientras que el mapa dibuja el camino real por
   * calles, que siempre es mas largo. Sin esto, el turista lee dos numeros
   * distintos para el mismo recorrido y ninguno le dice de donde sale cada uno.
   *
   * OSRM devuelve solo la ida, asi que el circuito es el doble: el modelo del
   * proyecto es de ida y vuelta a la misma estacion (RNF-04), sin combinaciones.
   *
   * El tiempo se reescala en la misma proporcion que la distancia, con lo que
   * se conserva el ritmo de caminata (min/km) que la tabla parametrica
   * dificultad fija para este nivel (RNF-06).
   */
  function armonizarDistancias(metrosIda) {
    var kmIda = (metrosIda / 1000).toFixed(2);
    var kmIdaVuelta = (parseFloat(kmIda) * 2).toFixed(2);

    var textoRuta = document.getElementById("texto-descripcion-ruta");
    if (textoRuta) {
      textoRuta.innerHTML = '<i class="bi bi-signpost-2-fill text-success me-1"></i>'
        + ' Trazado peatonal por calles: <strong>' + kmIda + ' km</strong> de ida '
        + '(<strong>' + kmIdaVuelta + ' km</strong> el circuito de ida y vuelta, '
        + 'vía OpenStreetMap).';
    }

    var elDistancia = document.getElementById("ficha-distancia-valor");
    if (elDistancia) {
      elDistancia.innerHTML = kmIdaVuelta
        + ' km <span class="badge bg-light text-dark border ms-1 fw-normal"'
        + ' style="font-size: 0.72rem;">por calles</span>';
    }

    var distHaversine = parseFloat(contenedor.dataset.distanciaHaversine);
    var tiempoHaversine = parseInt(contenedor.dataset.tiempoHaversine, 10);
    var elTiempo = document.getElementById("ficha-tiempo-valor");
    if (elTiempo && distHaversine > 0 && !isNaN(tiempoHaversine)) {
      var nuevoTiempo = Math.round((parseFloat(kmIdaVuelta) / distHaversine) * tiempoHaversine);
      elTiempo.textContent = nuevoTiempo + ' min';
    }
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

      if (data.routes[0].distance) {
        armonizarDistancias(data.routes[0].distance);
      }
    })
    .catch(function (err) {
      if (timerId) clearTimeout(timerId);
      console.info("Ruta peatonal OSRM no disponible, usando trazo directo:", err.message);
      dibujarTrazoDirecto();
    });
});
