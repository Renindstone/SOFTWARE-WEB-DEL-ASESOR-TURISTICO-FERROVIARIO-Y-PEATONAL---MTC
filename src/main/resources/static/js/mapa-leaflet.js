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
// El mapa es multimodal (RF-07): al elegir un servicio en el selector de
// trenes del formulario, actualizarTramoFerroviario() dibuja la via desde la
// estacion de abordaje hasta la estacion de partida de la caminata, con su
// marcador, y amplia la vista al itinerario completo. Al volver a "Sin
// servicio de tren" la via se retira y el mapa vuelve a la caminata.
//
// El tramo en tren sigue el trazado real de las vias: la red ferroviaria
// (OpenStreetMap, ver scripts/descargar_red_ferroviaria.py) se descarga como
// GeoJSON la primera vez que se elige un tren, se convierte en un grafo y el
// camino entre las dos estaciones se calcula con Dijkstra. Mientras llega, o
// si no llega, o si alguna estacion queda lejos de la via, se dibuja una
// recta marcada como esquematica: nunca se hace pasar una recta por la via.
//
// Las coordenadas llegan como data-attributes del contenedor del mapa, que la
// vista cliente/ruta-detalle.html rellena desde Estacion y ZonaTuristica; las
// del tren, como data-attributes de cada <option> del selector.
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

  // Capa base (CartoDB Voyager con clave, OpenStreetMap sin ella): la decide
  // mapa-base.js, que layout/base.html carga antes que este script.
  var capaBase = typeof crearCapaBase === "function"
    ? crearCapaBase(contenedor)
    : L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
        maxZoom: 19,
        attribution: "&copy; OpenStreetMap contributors"
      });
  capaBase.addTo(mapa);

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

  // ---- Estado del tramo en tren ----
  // La capa ferroviaria vive aparte para poder vaciarla sin tocar la caminata.
  // boundsPeatonal guarda el encuadre de la caminata (directo o por calles)
  // para volver a el al quitar el tren, y textoPeatonalGuardado la leyenda de
  // la caminata para restaurarla: arranca con la frase de Haversine que ya
  // trae la vista, y armonizarDistancias() la sustituye si OSRM responde.
  var capaFerroviaria = L.layerGroup().addTo(mapa);
  var boundsPeatonal = null;
  var textoRutaEl = document.getElementById("texto-descripcion-ruta");
  var textoPeatonalGuardado = textoRutaEl ? textoRutaEl.innerHTML : "";
  var selectTren = document.getElementById("idServicioTren");

  function hayTrenSeleccionado() {
    return !!(selectTren && selectTren.value);
  }

  // ---- Red ferroviaria real ----
  // red: {vertices: [[lat, lon]...], ady: [[{v, d}]...]} con d en km.
  // cargaRed guarda la promesa para que la descarga ocurra una sola vez.
  var urlRed = contenedor.dataset.redFerroviaria || "";
  var red = null;
  var cargaRed = null;
  var MAX_KM_ESTACION_A_VIA = 3;

  function haversineKm(a, b) {
    var R = 6371;
    var dLat = (b[0] - a[0]) * Math.PI / 180;
    var dLon = (b[1] - a[1]) * Math.PI / 180;
    var la1 = a[0] * Math.PI / 180;
    var la2 = b[0] * Math.PI / 180;
    var h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
      + Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return 2 * R * Math.asin(Math.sqrt(h));
  }

  // Los vertices se identifican por su coordenada redondeada a 5 decimales,
  // que es como los guarda el GeoJSON: dos vias que comparten un nodo en OSM
  // comparten aqui el mismo vertice, y asi el grafo queda conectado.
  function construirGrafo(geojson) {
    var indice = {};
    var vertices = [];
    var ady = [];

    function idDe(lat, lon) {
      var clave = lat.toFixed(5) + "," + lon.toFixed(5);
      if (!(clave in indice)) {
        indice[clave] = vertices.length;
        vertices.push([lat, lon]);
        ady.push([]);
      }
      return indice[clave];
    }

    (geojson.features || []).forEach(function (f) {
      if (!f.geometry || f.geometry.type !== "LineString") return;
      var cs = f.geometry.coordinates;
      for (var i = 0; i < cs.length - 1; i++) {
        var a = idDe(cs[i][1], cs[i][0]);
        var b = idDe(cs[i + 1][1], cs[i + 1][0]);
        var d = haversineKm(vertices[a], vertices[b]);
        ady[a].push({ v: b, d: d });
        ady[b].push({ v: a, d: d });
      }
    });
    return { vertices: vertices, ady: ady };
  }

  function cargarRed() {
    if (cargaRed) return cargaRed;
    if (!urlRed || typeof fetch !== "function") {
      cargaRed = Promise.resolve(null);
      return cargaRed;
    }
    cargaRed = fetch(urlRed)
      .then(function (res) {
        if (!res.ok) throw new Error("red ferroviaria " + res.status);
        return res.json();
      })
      .then(function (geojson) {
        red = construirGrafo(geojson);
        return red;
      })
      .catch(function (err) {
        console.info("Red ferroviaria no disponible, tramo en tren esquemático:", err.message);
        red = null;
        return null;
      });
    return cargaRed;
  }

  function verticeMasCercano(punto) {
    var mejor = -1;
    var mejorDist = Infinity;
    for (var i = 0; i < red.vertices.length; i++) {
      var d = haversineKm(punto, red.vertices[i]);
      if (d < mejorDist) {
        mejorDist = d;
        mejor = i;
      }
    }
    return { indice: mejor, km: mejorDist };
  }

  // Dijkstra con un monticulo binario minimo. La red tiene unos pocos miles
  // de vertices, asi que tarda milisegundos.
  function caminoMasCorto(desde, hasta) {
    var n = red.vertices.length;
    var dist = new Array(n);
    var previo = new Array(n);
    for (var i = 0; i < n; i++) { dist[i] = Infinity; previo[i] = -1; }
    dist[desde] = 0;

    var monticulo = [];
    function subir(pos) {
      while (pos > 0) {
        var padre = (pos - 1) >> 1;
        if (monticulo[padre].d <= monticulo[pos].d) break;
        var t = monticulo[padre]; monticulo[padre] = monticulo[pos]; monticulo[pos] = t;
        pos = padre;
      }
    }
    function bajar(pos) {
      for (;;) {
        var izq = 2 * pos + 1, der = izq + 1, menor = pos;
        if (izq < monticulo.length && monticulo[izq].d < monticulo[menor].d) menor = izq;
        if (der < monticulo.length && monticulo[der].d < monticulo[menor].d) menor = der;
        if (menor === pos) break;
        var t = monticulo[menor]; monticulo[menor] = monticulo[pos]; monticulo[pos] = t;
        pos = menor;
      }
    }
    function meter(item) { monticulo.push(item); subir(monticulo.length - 1); }
    function sacar() {
      var primero = monticulo[0];
      var ultimo = monticulo.pop();
      if (monticulo.length) { monticulo[0] = ultimo; bajar(0); }
      return primero;
    }

    meter({ v: desde, d: 0 });
    while (monticulo.length) {
      var actual = sacar();
      if (actual.v === hasta) break;
      if (actual.d > dist[actual.v]) continue;
      var vecinos = red.ady[actual.v];
      for (var j = 0; j < vecinos.length; j++) {
        var nd = actual.d + vecinos[j].d;
        if (nd < dist[vecinos[j].v]) {
          dist[vecinos[j].v] = nd;
          previo[vecinos[j].v] = actual.v;
          meter({ v: vecinos[j].v, d: nd });
        }
      }
    }
    if (dist[hasta] === Infinity) return null;

    var camino = [];
    for (var v = hasta; v !== -1; v = previo[v]) camino.push(v);
    camino.reverse();
    return { vertices: camino, km: dist[hasta] };
  }

  /**
   * Trazado por la via entre dos estaciones, o null si la red no esta
   * cargada, alguna estacion queda a mas de 3 km de la via (la de Urubamba
   * del catalogo, por ejemplo) o no hay camino. Los extremos son las propias
   * estaciones, unidas a la via por un corto tramo recto.
   */
  function trazadoFerroviario(desde, hasta) {
    if (!red) return null;
    var a = verticeMasCercano(desde);
    var b = verticeMasCercano(hasta);
    if (a.indice < 0 || b.indice < 0) return null;
    if (a.km > MAX_KM_ESTACION_A_VIA || b.km > MAX_KM_ESTACION_A_VIA) return null;
    var camino = caminoMasCorto(a.indice, b.indice);
    if (!camino) return null;
    var latLngs = [desde];
    camino.vertices.forEach(function (v) { latLngs.push(red.vertices[v]); });
    latLngs.push(hasta);
    return { latLngs: latLngs, km: camino.km + a.km + b.km };
  }

  // ---- Respaldo: trazo directo (Haversine) ----
  function dibujarTrazoDirecto() {
    L.polyline([
      [latOrigen, lonOrigen],
      [latDestino, lonDestino]
    ], { color: "#14453D", weight: 4, opacity: 0.85, dashArray: "8, 6" }).addTo(mapa);

    boundsPeatonal = L.latLngBounds([
      [latOrigen, lonOrigen],
      [latDestino, lonDestino]
    ]);
    mapa.fitBounds(boundsPeatonal.pad(0.35));

    // Si el turista eligio un tren mientras OSRM no contestaba, el encuadre
    // que manda es el del itinerario completo, no el de la caminata.
    if (hayTrenSeleccionado()) {
      actualizarTramoFerroviario();
    }
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
    // La leyenda peatonal definitiva es esta: es la que se restaura al quitar
    // el tren y la que va como segundo tramo de la leyenda multimodal.
    textoPeatonalGuardado = textoRuta ? textoRuta.innerHTML : "";

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

    // El eje de kilometros del perfil altimetrico sigue a la misma cifra:
    // la cima (la zona) queda a la distancia de ida y el extremo, al total.
    var elKmMedio = document.getElementById("altimetria-km-medio");
    var elKmTotal = document.getElementById("altimetria-km-total");
    if (elKmMedio) elKmMedio.textContent = kmIda;
    if (elKmTotal) elKmTotal.textContent = kmIdaVuelta;
  }

  // ---- Tramo en tren ----

  /**
   * Retira la via y el marcador con un fundido de 350 ms (la transicion
   * la ponen .via-tren y .marcador-tren-custom en estilos.css). Solo se
   * eliminan las capas que habia en ese momento: si el turista elige otro
   * tren antes de que termine el fundido, la via nueva no se ve afectada.
   */
  function retirarTramoFerroviario() {
    var capas = [];
    capaFerroviaria.eachLayer(function (capa) { capas.push(capa); });
    capas.forEach(function (capa) {
      if (typeof capa.setStyle === "function") {
        capa.setStyle({ opacity: 0 });
      } else if (typeof capa.setOpacity === "function") {
        capa.setOpacity(0);
      }
    });
    setTimeout(function () {
      capas.forEach(function (capa) { capaFerroviaria.removeLayer(capa); });
    }, 350);
  }

  /**
   * Dibuja el tramo en tren del servicio elegido en el selector: marcador en
   * la estacion de abordaje, via ferrea hasta la estacion de partida de la
   * caminata (que es donde llega el tren) y encuadre del itinerario completo.
   * Con "Sin servicio de tren" deshace todo y devuelve el mapa a la caminata.
   */
  function actualizarTramoFerroviario() {
    if (!selectTren) return;

    var opcion = selectTren.options[selectTren.selectedIndex];
    if (!opcion) return;
    var latTrenOrigen = parseFloat(opcion.dataset.latOrigen);
    var lonTrenOrigen = parseFloat(opcion.dataset.lonOrigen);
    var textoRuta = document.getElementById("texto-descripcion-ruta");

    // Sin tren elegido, o sin coordenadas para dibujarlo.
    if (!selectTren.value || isNaN(latTrenOrigen) || isNaN(lonTrenOrigen)) {
      retirarTramoFerroviario();
      if (boundsPeatonal) {
        mapa.fitBounds(boundsPeatonal.pad(0.25));
      }
      if (textoRuta && textoPeatonalGuardado) {
        textoRuta.innerHTML = textoPeatonalGuardado;
      }
      return;
    }

    // Cambio de un tren a otro: la via anterior se sustituye en el acto.
    capaFerroviaria.clearLayers();

    var nombreTrenOrigen = opcion.dataset.nombreOrigen || "Estación de tren";
    var salida = opcion.dataset.salida || "";
    var llegada = opcion.dataset.llegada || "";
    var tiempo = opcion.dataset.tiempo || "";
    var tarifa = opcion.dataset.tarifa || "";
    var duracion = tiempo ? " (" + tiempo + " min)" : "";

    // 1. Icono de tren para la estacion de abordaje (.marcador-tren en
    //    estilos.css; className sustituye al recuadro blanco por defecto).
    var iconoTren = L.divIcon({
      className: "marcador-tren-custom",
      html: '<div class="marcador-tren"><i class="bi bi-train-front-fill"></i></div>',
      iconSize: [32, 32],
      iconAnchor: [16, 16],
      popupAnchor: [0, -18]
    });

    // 2. Marcador de inicio del tren
    var markerTren = L.marker([latTrenOrigen, lonTrenOrigen], { icon: iconoTren })
      .bindPopup(
        '<div class="p-1">' +
          '<span class="badge insignia-tren mb-1"><i class="bi bi-train-front me-1"></i>Abordaje de tren</span><br>' +
          '<strong>' + nombreTrenOrigen + '</strong><br>' +
          '<small class="text-muted">Horario: ' + salida + ' &rarr; ' + llegada + duracion + '</small><br>' +
          '<small class="text-success fw-bold">Tarifa referencial: S/ ' + tarifa + '</small>' +
        '</div>'
      );
    capaFerroviaria.addLayer(markerTren);

    // 3. Trazo ferroviario con estilo cartografico de via de tren: riel base
    //    oscuro y, encima, una linea blanca discontinua a modo de durmientes.
    //    Sigue la via real si la red ya esta cargada; si no, una recta
    //    esquematica que se sustituye en cuanto la red llega.
    var trazado = trazadoFerroviario([latTrenOrigen, lonTrenOrigen], [latOrigen, lonOrigen]);
    var tramoTrenCoords = trazado ? trazado.latLngs : [
      [latTrenOrigen, lonTrenOrigen],
      [latOrigen, lonOrigen]
    ];
    if (!trazado && !red) {
      cargarRed().then(function (cargada) {
        if (cargada && hayTrenSeleccionado()) {
          actualizarTramoFerroviario();
        }
      });
    }
    var viaBase = L.polyline(tramoTrenCoords, {
      className: "via-tren",
      color: "#1B365D",
      weight: 6,
      opacity: 0.9
    });
    capaFerroviaria.addLayer(viaBase);

    var viaDurmientes = L.polyline(tramoTrenCoords, {
      className: "via-tren",
      color: "#FFFFFF",
      weight: 3,
      opacity: 0.85,
      dashArray: "7, 7"
    });
    capaFerroviaria.addLayer(viaDurmientes);

    // 4. Encuadre multimodal: toda la via recorrida, la estacion de
    //    transbordo y el destino peatonal.
    var boundsMultimodal = L.latLngBounds(tramoTrenCoords)
      .extend([latOrigen, lonOrigen])
      .extend([latDestino, lonDestino]);
    mapa.fitBounds(boundsMultimodal.pad(0.18));

    // 5. Leyenda multimodal al pie del mapa. Dice de donde sale el trazo:
    //    kilometros de via reales, o una recta que no sigue la via.
    var detalleVia = trazado
      ? Math.round(trazado.km) + ' km de vía'
      : 'tramo esquemático, no sigue la vía';
    if (textoRuta) {
      textoRuta.innerHTML =
        '<div class="leyenda-multimodal">' +
          '<div><i class="bi bi-train-front texto-riel me-1"></i><strong>Tramo ferroviario:</strong> ' +
            nombreTrenOrigen + ' &rarr; ' + nombreOrigen + ', ' + detalleVia +
            (tiempo ? ' (' + tiempo + ' min de viaje en tren, salida ' + salida + ').' : ' (salida ' + salida + ').') +
          '</div>' +
          '<div><i class="bi bi-person-walking text-success me-1"></i><strong>Tramo peatonal:</strong> ' +
            (textoPeatonalGuardado || (nombreOrigen + ' &rarr; ' + nombreDestino)) + '</div>' +
        '</div>';
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

      boundsPeatonal = polyCalles.getBounds();
      mapa.fitBounds(boundsPeatonal.pad(0.25));

      if (data.routes[0].distance) {
        armonizarDistancias(data.routes[0].distance);
      }

      // Con un tren ya elegido, la leyenda y el encuadre se rehacen con la
      // caminata definitiva (por calles) en vez de la estimada.
      if (hayTrenSeleccionado()) {
        actualizarTramoFerroviario();
      }
    })
    .catch(function (err) {
      if (timerId) clearTimeout(timerId);
      console.info("Ruta peatonal OSRM no disponible, usando trazo directo:", err.message);
      dibujarTrazoDirecto();
    });

  // ---- Selector de tren ----
  if (selectTren) {
    selectTren.addEventListener("change", actualizarTramoFerroviario);
    // El navegador puede devolver la pagina con una opcion ya elegida (al
    // volver atras, por ejemplo): se dibuja sin esperar a ningun cambio.
    if (selectTren.value) {
      actualizarTramoFerroviario();
    }
  }
});
