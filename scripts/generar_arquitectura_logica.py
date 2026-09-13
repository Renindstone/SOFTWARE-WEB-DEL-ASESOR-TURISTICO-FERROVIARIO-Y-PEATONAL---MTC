# -*- coding: utf-8 -*-
"""Genera la figura "Arquitectura logica del sistema" (Figura 2 del documento
del proyecto y Figura 1 del manual de usuario).

Uso: python scripts/generar_arquitectura_logica.py -> arquitectura_logica.png
en el directorio actual. Para actualizar los .docx, convertir a JPEG y
sustituir word/media/image5.jpeg (proyecto) y word/media/image3.jpeg (manual)
conservando el nombre, ya que las dimensiones (1376 x 697 de proporcion) y las
referencias del XML no cambian."""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

for f in ("C:/Windows/Fonts/calibri.ttf", "C:/Windows/Fonts/calibrib.ttf"):
    font_manager.fontManager.addfont(f)
plt.rcParams["font.family"] = "Calibri"

W, H = 13.76, 6.97          # misma proporcion que la imagen original (1376 x 697)
fig = plt.figure(figsize=(W, H), dpi=200)
ax = fig.add_axes([0, 0, 1, 1])
ax.set_xlim(0, W)
ax.set_ylim(0, H)
ax.axis("off")

ax.text(W / 2, H - 0.22, "Arquitectura Lógica del Sistema — Asesor Turístico Ferroviario y Peatonal (MTC)",
        ha="center", va="top", fontsize=15.5, fontweight="bold", color="#111111")

BORDE = "#3C3C3C"
CAPAS = [
    ("CAPA DE PRESENTACIÓN (WEB RESPONSIVE)", "#D9E2F3",
     ["Controladores Spring MVC · Vistas Thymeleaf · Bootstrap 5 · Leaflet.js (mapa multimodal y geoselector)",
      "Paneles por rol: Turista / Travel Group Perú / PeruRail / Admin MTC"]),
    ("CAPA DE NEGOCIO / ORQUESTACIÓN", "#E2EFDA",
     ["PreferenciaService · ZonaTuristicaService · AforoService · RutaPeatonalService (motor de rutas)",
      "InformeService · ServicioTrenService · TarifaService · UsuarioService · AuditoriaService",
      "Tareas programadas @Scheduled: SincronizacionPeruRailJob · SincronizacionSenamhiJob"]),
    ("CAPA DE INTEGRACIÓN DE APIs", "#FBE5D6",
     ["PeruRailClient · SenamhiClient (RestClient)",
      "Módulo de carga Travel Group Perú (CRUD interno)"]),
    ("CAPA DE PERSISTENCIA", "#EDEDED",
     ["Spring Data JPA · Hibernate ORM · PostgreSQL 15",
      "Entidades con prefijos normalizados (Est, Zon, Rut, Cli, Ser, Inf, Aud…)"]),
]

x0, ancho = 1.75, 6.75
alto = 1.27
sep = 0.21
y_top = H - 0.88
centros = []
for i, (titulo, color, lineas) in enumerate(CAPAS):
    y = y_top - i * (alto + sep) - alto
    caja = FancyBboxPatch((x0, y), ancho, alto, boxstyle="round,pad=0,rounding_size=0.08",
                          linewidth=1.6, edgecolor=BORDE, facecolor=color)
    ax.add_patch(caja)
    cx = x0 + ancho / 2
    n = len(lineas)
    ax.text(cx, y + alto - 0.24, titulo, ha="center", va="center", fontsize=13.2, fontweight="bold", color="#111111")
    paso = 0.235 if n == 3 else 0.26
    # bloque de lineas centrado en el espacio que queda bajo el titulo
    inicio = y + (alto - 0.44) / 2 + (n - 1) * paso / 2
    for j, linea in enumerate(lineas):
        ax.text(cx, inicio - j * paso, linea, ha="center", va="center", fontsize=9.6 if n == 3 else 10.2, color="#111111")
    centros.append((y, y + alto))
    if i > 0:
        y_ant = centros[i - 1][0]
        ax.add_patch(FancyArrowPatch((cx, y_ant), (cx, y + alto), arrowstyle="-|>", mutation_scale=14,
                                     linewidth=1.3, color="#666666", shrinkA=0, shrinkB=0))

def lateral(x, y, w, h, texto, destino_y):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0,rounding_size=0.08",
                                linewidth=1.5, edgecolor="#222222", facecolor="white", linestyle=(0, (5, 3))))
    ax.text(x + w / 2, y + h / 2, texto, ha="center", va="center", fontsize=10.2, color="#111111", linespacing=1.35)
    ax.add_patch(FancyArrowPatch((x, y + h / 2), (x0 + ancho, destino_y), arrowstyle="-|>", mutation_scale=13,
                                 linewidth=1.3, color="#666666", shrinkA=0, shrinkB=0))

# usuarios -> presentacion
y_pres = centros[0]
lateral(8.95, y_pres[0] + 0.18, 2.7, 0.95, "Usuarios y Operadores Web\n(Turista, Travel Group Perú,\nPeruRail y Admin MTC)",
        (y_pres[0] + y_pres[1]) / 2)
# externas -> integracion
y_int = centros[2]
lateral(8.95, y_int[1] - 0.05, 2.7, 0.95, "PeruRail\n(API externa: estaciones,\nhorarios, tarifas)", y_int[1] - 0.25)
lateral(8.95, y_int[0] - 0.42, 2.7, 0.95, "SENAMHI\n(API externa: previsión\nclimática)", y_int[0] + 0.3)

fig.savefig("arquitectura_logica.png", dpi=200, facecolor="white")
print("ok")
