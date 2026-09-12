// Capa base compartida por los dos mapas del sistema (mapa-leaflet.js y
// mapa-buscador.js). Se carga antes que ellos desde layout/base.html.
//
// Con clave de CARTO se usan las teselas CartoDB Voyager: la misma
// cartografia de OpenStreetMap con un renderizado mas legible, tipografia
// limpia y version Retina ({r} -> @2x), sin coste (RNF-02). La clave la pone
// la vista en data-carto-key del contenedor del mapa (MapaBaseAdvice la lee
// de mapa.carto.api-key / CARTO_API_KEY): es gratuita pero obligatoria,
// porque sin ella CARTO estampa "API KEY REQUIRED" sobre cada tesela. Por
// eso, sin clave, se recurre a las teselas estandar de OpenStreetMap.
//
// La atribucion debe nombrar a OpenStreetMap y a CARTO: es la condicion del
// plan gratuito.
function crearCapaBase(contenedor) {
  var clave = "";
  if (contenedor && contenedor.dataset && contenedor.dataset.cartoKey) {
    clave = contenedor.dataset.cartoKey.trim();
  }

  function capaOpenStreetMap() {
    return L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: "&copy; <a href=\"https://www.openstreetmap.org/copyright\" target=\"_blank\" rel=\"noopener\">OpenStreetMap</a> contributors"
    });
  }

  if (!clave) {
    return capaOpenStreetMap();
  }

  var capaVoyager = L.tileLayer(
    "https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png?key=" + encodeURIComponent(clave),
    {
      maxZoom: 20,
      attribution: "&copy; <a href=\"https://www.openstreetmap.org/copyright\" target=\"_blank\" rel=\"noopener\">OpenStreetMap</a> contributors"
        + " &copy; <a href=\"https://carto.com/attributions\" target=\"_blank\" rel=\"noopener\">CARTO</a>"
    }
  );

  // Si el CDN de CARTO no responde (corte de red hacia ese dominio), el mapa
  // cambia a OpenStreetMap en vez de quedarse con huecos grises. Una clave
  // invalida no se detecta aqui: CARTO responde 200 con la tesela marcada.
  capaVoyager.once("tileerror", function () {
    var mapa = capaVoyager._map;
    if (mapa) {
      mapa.removeLayer(capaVoyager);
      capaOpenStreetMap().addTo(mapa);
    }
  });

  return capaVoyager;
}
