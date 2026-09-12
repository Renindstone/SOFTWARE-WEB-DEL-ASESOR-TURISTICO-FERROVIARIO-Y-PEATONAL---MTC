# -*- coding: utf-8 -*-
"""Descarga de OpenStreetMap el trazado real de las vias de PeruRail y lo
guarda como GeoJSON estatico para el mapa multimodal (mapa-leaflet.js).

Fuente: relaciones OSM "Ferrocarril Santa Ana" (Cusco - Ollantaytambo -
Machu Picchu - Hidroelectrica) y "Ferrocarril del Sur del Peru" (Arequipa -
Juliaca - Puno / Cusco). Los datos son (c) OpenStreetMap contributors, bajo
licencia ODbL; el mapa ya muestra esa atribucion.

Se ejecuta a mano, solo cuando haga falta refrescar el trazado:

    python scripts/descargar_red_ferroviaria.py

No necesita mas que la biblioteca estandar. Escribe
src/main/resources/static/js/geo/red-ferroviaria.geojson (bajo /js/ porque
SecurityConfig solo abre /css/**, /js/** e /img/** a los visitantes
anonimos, igual que las fuentes de tokens.css).

Que hace con las vias:
  1. Se queda con la caja que contiene a las ocho estaciones del proyecto
     (deja fuera el ramal Matarani - Arequipa).
  2. Simplifica cada via con Douglas-Peucker (unos 25 m de tolerancia), pero
     nunca quita un nodo compartido por dos vias: son los cruces y empalmes,
     y sin ellos el grafo que arma mapa-leaflet.js quedaria partido.
  3. Redondea a cinco decimales (~1 m) para que el archivo pese poco.
  4. Empalma los tramos que OSM deja sueltos: las relaciones no incluyen
     las vias de patio que unen, por ejemplo, Wanchaq con San Pedro en
     Cusco (1,6 km) o la estacion de Juliaca con el ramal a Puno. Cuando
     dos componentes de la red quedan a menos de EMPALME_MAX_KM, se anade
     un tramo recto entre sus vertices mas proximos, marcado como empalme.
"""
import datetime
import json
import math
import os
import sys
import urllib.parse
import urllib.request

OVERPASS = "https://overpass-api.de/api/interpreter"

# Ferrocarril Santa Ana (1179376) y Ferrocarril del Sur del Peru (1082979).
CONSULTA = """
[out:json][timeout:180];
(
  relation(1179376);
  relation(1082979);
);
way(r);
out geom;
"""

# Caja que abarca las estaciones sembradas (Arequipa, Puno, Cusco, valle
# sagrado, Machu Picchu e Hidroelectrica) con margen.
LAT_MIN, LAT_MAX = -16.6, -13.0
LON_MIN, LON_MAX = -72.7, -69.8

TOLERANCIA_GRADOS = 0.00022   # ~25 m a esta latitud
DECIMALES = 5
EMPALME_MAX_KM = 2.5

SALIDA = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                      "static", "js", "geo", "red-ferroviaria.geojson")


def descargar():
    datos = urllib.parse.urlencode({"data": CONSULTA}).encode("utf-8")
    peticion = urllib.request.Request(OVERPASS, data=datos,
                                      headers={"User-Agent": "asesor-turistico-mtc/1.0"})
    with urllib.request.urlopen(peticion, timeout=240) as respuesta:
        return json.load(respuesta)


def distancia_punto_segmento(p, a, b):
    """Distancia (en grados, suficiente para simplificar) de p al segmento ab."""
    ax, ay = a
    bx, by = b
    px, py = p
    dx, dy = bx - ax, by - ay
    if dx == 0 and dy == 0:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def douglas_peucker(puntos, tolerancia):
    """Simplificacion clasica; conserva siempre el primer y el ultimo punto."""
    if len(puntos) < 3:
        return list(puntos)
    a, b = puntos[0], puntos[-1]
    indice, maximo = 0, -1.0
    for i in range(1, len(puntos) - 1):
        d = distancia_punto_segmento(puntos[i], a, b)
        if d > maximo:
            indice, maximo = i, d
    if maximo > tolerancia:
        izquierda = douglas_peucker(puntos[:indice + 1], tolerancia)
        derecha = douglas_peucker(puntos[indice:], tolerancia)
        return izquierda[:-1] + derecha
    return [a, b]


def simplificar_conservando(puntos, conservar, tolerancia):
    """Aplica Douglas-Peucker tramo a tramo entre los puntos que hay que conservar."""
    resultado = []
    inicio = 0
    for i in range(1, len(puntos)):
        if i in conservar or i == len(puntos) - 1:
            tramo = douglas_peucker(puntos[inicio:i + 1], tolerancia)
            if resultado:
                tramo = tramo[1:]
            resultado.extend(tramo)
            inicio = i
    return resultado


def haversine_km(a, b):
    """a y b como (lon, lat)."""
    radio = 6371.0
    lon1, lat1, lon2, lat2 = map(math.radians, [a[0], a[1], b[0], b[1]])
    h = (math.sin((lat2 - lat1) / 2) ** 2
         + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2)
    return 2 * radio * math.asin(math.sqrt(h))


def componentes(features):
    """Agrupa los vertices (por coordenada) en componentes conexos."""
    adyacencia = {}
    for f in features:
        cs = [tuple(c) for c in f["geometry"]["coordinates"]]
        for a, b in zip(cs, cs[1:]):
            adyacencia.setdefault(a, set()).add(b)
            adyacencia.setdefault(b, set()).add(a)
    vistos = set()
    grupos = []
    for inicio in adyacencia:
        if inicio in vistos:
            continue
        grupo, pila = [], [inicio]
        vistos.add(inicio)
        while pila:
            v = pila.pop()
            grupo.append(v)
            for w in adyacencia[v]:
                if w not in vistos:
                    vistos.add(w)
                    pila.append(w)
        grupos.append(grupo)
    return grupos


def empalmar(features):
    """Une con tramos rectos los componentes que quedan casi tocandose."""
    empalmes = 0
    while True:
        grupos = componentes(features)
        if len(grupos) < 2:
            break
        mejor = None
        for i in range(len(grupos)):
            for j in range(i + 1, len(grupos)):
                for a in grupos[i]:
                    for b in grupos[j]:
                        d = haversine_km(a, b)
                        if mejor is None or d < mejor[0]:
                            mejor = (d, a, b)
        if mejor is None or mejor[0] > EMPALME_MAX_KM:
            break
        d, a, b = mejor
        features.append({
            "type": "Feature",
            "properties": {"empalme": True, "km": round(d, 2)},
            "geometry": {"type": "LineString", "coordinates": [list(a), list(b)]},
        })
        empalmes += 1
        print("  empalme de %.2f km entre %s y %s" % (d, a, b))
    return empalmes, len(componentes(features))


def dentro_de_caja(geometria):
    return any(LAT_MIN <= p["lat"] <= LAT_MAX and LON_MIN <= p["lon"] <= LON_MAX
               for p in geometria)


def main():
    print("Descargando vias de Overpass...")
    respuesta = descargar()
    vias = [e for e in respuesta["elements"] if e["type"] == "way" and "geometry" in e]
    vias = [v for v in vias
            if v.get("tags", {}).get("railway") in ("rail", "narrow_gauge")
            and dentro_de_caja(v["geometry"])]
    print("  vias utiles:", len(vias))

    # Nodos que aparecen en mas de una via: cruces y empalmes que no se tocan.
    apariciones = {}
    for v in vias:
        for n in v["nodes"]:
            apariciones[n] = apariciones.get(n, 0) + 1

    features = []
    puntos_antes = puntos_despues = 0
    for v in vias:
        puntos = [(p["lon"], p["lat"]) for p in v["geometry"]]
        conservar = {i for i, n in enumerate(v["nodes"]) if apariciones.get(n, 0) > 1}
        simplificados = simplificar_conservando(puntos, conservar, TOLERANCIA_GRADOS)
        puntos_antes += len(puntos)
        puntos_despues += len(simplificados)
        features.append({
            "type": "Feature",
            "properties": {"id": v["id"]},
            "geometry": {
                "type": "LineString",
                "coordinates": [[round(x, DECIMALES), round(y, DECIMALES)] for x, y in simplificados],
            },
        })

    empalmes, sueltos = empalmar(features)
    print("  empalmes anadidos: %d; componentes que quedan: %d" % (empalmes, sueltos))

    coleccion = {
        "type": "FeatureCollection",
        "fuente": "OpenStreetMap contributors (ODbL): relaciones Ferrocarril Santa Ana "
                  "(1179376) y Ferrocarril del Sur del Peru (1082979)",
        "descargado": datetime.date.today().isoformat(),
        "features": features,
    }

    os.makedirs(os.path.dirname(SALIDA), exist_ok=True)
    with open(SALIDA, "w", encoding="utf-8") as f:
        json.dump(coleccion, f, ensure_ascii=False, separators=(",", ":"))
    print("  puntos: %d -> %d" % (puntos_antes, puntos_despues))
    print("  escrito %s (%d KB)" % (os.path.normpath(SALIDA), os.path.getsize(SALIDA) // 1024))
    return 0


if __name__ == "__main__":
    sys.exit(main())
