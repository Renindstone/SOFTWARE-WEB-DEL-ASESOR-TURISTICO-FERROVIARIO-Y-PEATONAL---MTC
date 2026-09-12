package com.turismo.util;

import com.turismo.dto.InformeConsolidadoDTO;
import com.turismo.dto.VisitanteDTO;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * Genera la representacion PDF del informe consolidado (RF-08) a partir
 * de InformeConsolidadoDTO, usando OpenPDF 2.0.5 (com.github.librepdf:openpdf,
 * paquete com.lowagie.text; fijado en 2.x porque la serie 3.x requiere Java 21).
 *
 * El documento se compone como un voucher de itinerario y no como un volcado
 * tabular: membrete institucional, bloque de trayecto, tarjetas de recorrido,
 * desglose sin cuadricula y caja de total. La paleta es la misma que la de la
 * version imprimible (informes/informe-pdf.html), de modo que el turista
 * reconozca el mismo documento venga de donde venga.
 */
@Component
public class GeneradorPdf {

    // Paleta institucional, equivalente a la de /css/tokens.css.
    private static final Color COLOR_MTC = new Color(20, 69, 61);          // #14453D
    private static final Color COLOR_ORO = new Color(200, 155, 60);        // #C89B3C
    private static final Color COLOR_FONDO_SUAVE = new Color(248, 247, 244); // #F8F7F4
    private static final Color COLOR_LINEA = new Color(226, 224, 216);     // #E2E0D8
    private static final Color COLOR_TEXTO = new Color(23, 32, 28);        // #17201C
    private static final Color COLOR_MUTED = new Color(95, 107, 98);       // #5F6B62
    private static final Color COLOR_BLANCO = Color.WHITE;

    // OpenPDF 2.x (com.lowagie.text.Font) usa la familia como constante int
    // (Font.HELVETICA), no el enum Font.FontFamily.HELVETICA de la serie 3.x.
    private static final Font FUENTE_TITULO = new Font(Font.HELVETICA, 16, Font.BOLD, COLOR_MTC);
    private static final Font FUENTE_SUBTITULO = new Font(Font.HELVETICA, 9, Font.NORMAL, COLOR_MUTED);
    private static final Font FUENTE_ENTIDAD = new Font(Font.HELVETICA, 8, Font.BOLD, COLOR_MTC);
    private static final Font FUENTE_CODIGO = new Font(Font.COURIER, 12, Font.BOLD, COLOR_MTC);
    private static final Font FUENTE_SECCION = new Font(Font.HELVETICA, 9, Font.BOLD, COLOR_MTC);
    private static final Font FUENTE_CABECERA_TABLA = new Font(Font.HELVETICA, 9, Font.BOLD, COLOR_BLANCO);
    private static final Font FUENTE_ETIQUETA = new Font(Font.HELVETICA, 8, Font.NORMAL, COLOR_MUTED);
    private static final Font FUENTE_VALOR = new Font(Font.HELVETICA, 9, Font.NORMAL, COLOR_TEXTO);
    private static final Font FUENTE_VALOR_FUERTE = new Font(Font.HELVETICA, 11, Font.BOLD, COLOR_TEXTO);
    private static final Font FUENTE_TOTAL = new Font(Font.HELVETICA, 13, Font.BOLD, COLOR_MTC);
    private static final Font FUENTE_AVISO = new Font(Font.HELVETICA, 7, Font.NORMAL, COLOR_MUTED);

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] generar(InformeConsolidadoDTO informe) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 42, 42, 44, 42);

        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();

            agregarMembrete(documento, informe);
            agregarTrayecto(documento, informe);
            agregarTarjetasDeRecorrido(documento, informe);
            agregarDetalleDelGrupo(documento, informe);
            agregarTotal(documento, informe);
            agregarPie(documento, informe);

            documento.close();
        } catch (DocumentException ex) {
            throw new IllegalStateException("No se pudo generar el PDF del informe consolidado", ex);
        }

        return salida.toByteArray();
    }

    // ------------------------------------------------------------------
    // Membrete: entidad a la izquierda, sello con el codigo a la derecha.
    // ------------------------------------------------------------------
    private void agregarMembrete(Document documento, InformeConsolidadoDTO informe) throws DocumentException {
        documento.add(filete(COLOR_MTC, 4f));
        documento.add(filete(COLOR_ORO, 2f));

        PdfPTable membrete = new PdfPTable(2);
        membrete.setWidthPercentage(100);
        membrete.setWidths(new float[]{2.1f, 1f});
        membrete.setSpacingBefore(12f);
        membrete.setSpacingAfter(16f);

        PdfPCell celdaEntidad = new PdfPCell();
        celdaEntidad.addElement(new Paragraph("MINISTERIO DE TRANSPORTES Y COMUNICACIONES", FUENTE_ENTIDAD));
        celdaEntidad.addElement(new Paragraph("Asesor Turístico Ferroviario y Peatonal", FUENTE_SUBTITULO));
        celdaEntidad.setBorder(Rectangle.NO_BORDER);
        celdaEntidad.setPadding(4f);
        membrete.addCell(celdaEntidad);

        Paragraph codigo = new Paragraph(
                informe.getCodigo() == null ? "-" : informe.getCodigo(), FUENTE_CODIGO);
        codigo.setAlignment(Element.ALIGN_RIGHT);
        Paragraph visita = new Paragraph("Visita del " + formatearFecha(informe), FUENTE_ETIQUETA);
        visita.setAlignment(Element.ALIGN_RIGHT);

        PdfPCell celdaSello = new PdfPCell();
        celdaSello.addElement(codigo);
        celdaSello.addElement(visita);
        celdaSello.setBackgroundColor(COLOR_FONDO_SUAVE);
        celdaSello.setBorder(Rectangle.BOX);
        celdaSello.setBorderColor(COLOR_LINEA);
        celdaSello.setPadding(8f);
        membrete.addCell(celdaSello);

        documento.add(membrete);
    }

    // ------------------------------------------------------------------
    // Trayecto: la conexion origen -> destino, en una banda suave.
    // ------------------------------------------------------------------
    private void agregarTrayecto(Document documento, InformeConsolidadoDTO informe) throws DocumentException {
        PdfPTable banda = new PdfPTable(1);
        banda.setWidthPercentage(100);
        banda.setSpacingAfter(14f);

        Paragraph titulo = new Paragraph();
        titulo.add(new Chunk(texto(informe.getEstacionOrigen()), FUENTE_TITULO));
        titulo.add(new Chunk("  →  ", new Font(Font.HELVETICA, 16, Font.BOLD, COLOR_ORO)));
        titulo.add(new Chunk(texto(informe.getZonaDestino()), FUENTE_TITULO));
        titulo.setLeading(0f, 1.3f);
        titulo.setSpacingBefore(3f);
        titulo.setSpacingAfter(3f);

        Integer personas = informe.getTotalPersonas();
        Paragraph meta = new Paragraph(formatearFecha(informe)
                + "  ·  " + (personas == null ? "-" : personas)
                + (personas != null && personas == 1 ? " persona" : " personas"), FUENTE_SUBTITULO);

        // Celda compuesta: cada parrafo conserva su propio interlineado. Con un
        // unico parrafo, la celda impone el suyo y la fecha en 9pt acaba
        // pisando el titulo en 16pt.
        PdfPCell celda = new PdfPCell();
        celda.addElement(new Paragraph("ITINERARIO", FUENTE_ETIQUETA));
        celda.addElement(titulo);
        celda.addElement(meta);
        celda.setBackgroundColor(COLOR_FONDO_SUAVE);
        celda.setBorder(Rectangle.BOX);
        celda.setBorderColor(COLOR_LINEA);
        celda.setPadding(14f);
        banda.addCell(celda);

        documento.add(banda);
    }

    // ------------------------------------------------------------------
    // Tarjetas 2x2 con lo que el turista mira primero.
    // ------------------------------------------------------------------
    private void agregarTarjetasDeRecorrido(Document documento, InformeConsolidadoDTO informe)
            throws DocumentException {
        PdfPTable tarjetas = new PdfPTable(2);
        tarjetas.setWidthPercentage(100);
        tarjetas.setSpacingAfter(18f);

        String distancia = "—";
        String tiempo = "";
        String dificultad = "—";
        if (informe.getRuta() != null) {
            distancia = informe.getRuta().getDistanciaKm() + " km";
            tiempo = informe.getRuta().getTiempoEstimadoMin() + " min · ida y vuelta";
            dificultad = texto(informe.getRuta().getDificultad());
        }
        tarjetas.addCell(tarjeta("RECORRIDO A PIE", distancia, tiempo));
        tarjetas.addCell(tarjeta("DIFICULTAD", dificultad, "Según el desnivel y la distancia"));

        String clima = "Sin pronóstico";
        String detalleClima = "SENAMHI no publicó datos para esta fecha";
        if (informe.getEstadoClima() != null) {
            clima = informe.getTemperaturaMinimaC() + "° / " + informe.getTemperaturaMaximaC() + "°C";
            detalleClima = informe.getEstadoClima() + " · " + informe.getProbabilidadLluvia() + "% de lluvia";
        }
        tarjetas.addCell(tarjeta("CLIMA PREVISTO", clima, detalleClima));

        String aforo = informe.getCupoDisponible() == null
                ? "Libre" : String.valueOf(informe.getCupoDisponible());
        String detalleAforo = informe.getCupoDisponible() == null
                ? "La zona no controla aforo diario" : "Cupos restantes tras esta reserva";
        tarjetas.addCell(tarjeta("AFORO DE LA ZONA", aforo, detalleAforo));

        documento.add(tarjetas);
    }

    private PdfPCell tarjeta(String titulo, String dato, String apunte) {
        Paragraph valor = new Paragraph(dato, FUENTE_VALOR_FUERTE);
        valor.setSpacingBefore(2f);
        valor.setSpacingAfter(2f);

        PdfPCell celda = new PdfPCell();
        celda.addElement(new Paragraph(titulo, FUENTE_ETIQUETA));
        celda.addElement(valor);
        if (apunte != null && !apunte.isBlank()) {
            celda.addElement(new Paragraph(apunte, FUENTE_ETIQUETA));
        }
        celda.setBorder(Rectangle.BOX);
        celda.setBorderColor(COLOR_LINEA);
        celda.setPadding(11f);
        return celda;
    }

    /**
     * RF-19: desglose por edad. Deja ver de donde sale el total cuando viaja
     * mas de una persona, que es justo lo que el turista quiere comprobar.
     *
     * Sin cuadricula: solo una regla inferior por fila y un sombreado alterno,
     * que es lo que sostiene la lectura de una tabla larga sin encerrarla.
     */
    private void agregarDetalleDelGrupo(Document documento, InformeConsolidadoDTO informe)
            throws DocumentException {
        if (informe.getVisitantes() == null || informe.getVisitantes().isEmpty()) {
            return;
        }

        Paragraph titulo = new Paragraph("QUÉ PAGA CADA PERSONA", FUENTE_SECCION);
        titulo.setSpacingAfter(2f);
        documento.add(titulo);

        Paragraph aclaracion = new Paragraph(
                "Los dos proveedores aplican tramos de edad distintos, así que una misma "
                        + "persona puede entrar en una categoría en el tren y en otra en la zona.",
                FUENTE_ETIQUETA);
        aclaracion.setSpacingAfter(8f);
        documento.add(aclaracion);

        PdfPTable detalle = new PdfPTable(5);
        detalle.setWidthPercentage(100);
        detalle.setWidths(new float[]{0.9f, 0.8f, 1.5f, 1.5f, 1f});
        detalle.setHeaderRows(1);

        String[] cabeceras = {"Edad", "Personas", "Tarifa tren", "Entrada zona", "Subtotal"};
        for (int i = 0; i < cabeceras.length; i++) {
            PdfPCell celda = new PdfPCell(new Paragraph(cabeceras[i], FUENTE_CABECERA_TABLA));
            celda.setBackgroundColor(COLOR_MTC);
            celda.setBorder(Rectangle.NO_BORDER);
            celda.setPadding(6f);
            celda.setHorizontalAlignment(i == cabeceras.length - 1 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            detalle.addCell(celda);
        }

        boolean alterna = false;
        for (VisitanteDTO visitante : informe.getVisitantes()) {
            Color fondo = alterna ? COLOR_FONDO_SUAVE : COLOR_BLANCO;
            agregarCelda(detalle, visitante.getEdad() + " años", fondo, Element.ALIGN_LEFT);
            agregarCelda(detalle, String.valueOf(visitante.getCantidad()), fondo, Element.ALIGN_LEFT);
            agregarCelda(detalle, (visitante.getCategoriaTren() == null
                    ? "Sin tren" : visitante.getCategoriaTren())
                    + "   S/ " + visitante.getSubtotalTren(), fondo, Element.ALIGN_LEFT);
            agregarCelda(detalle, visitante.getCategoriaZona()
                    + "   S/ " + visitante.getSubtotalZona(), fondo, Element.ALIGN_LEFT);
            agregarCelda(detalle, "S/ " + visitante.getSubtotal(), fondo, Element.ALIGN_RIGHT);
            alterna = !alterna;
        }

        documento.add(detalle);
    }

    // ------------------------------------------------------------------
    // Caja de total, con filete dorado.
    // ------------------------------------------------------------------
    private void agregarTotal(Document documento, InformeConsolidadoDTO informe) throws DocumentException {
        PdfPTable caja = new PdfPTable(2);
        caja.setWidthPercentage(100);
        caja.setWidths(new float[]{2.2f, 1f});
        caja.setSpacingBefore(16f);

        PdfPCell celdaRotulo = new PdfPCell();
        celdaRotulo.addElement(new Paragraph("TOTAL ESTIMADO DEL GRUPO", FUENTE_SECCION));
        celdaRotulo.addElement(new Paragraph("Tarifa referencial: no constituye boleto de transporte "
                + "ni reserva comercial emitida.", FUENTE_AVISO));
        celdaRotulo.setBackgroundColor(COLOR_FONDO_SUAVE);
        celdaRotulo.setBorder(Rectangle.BOX);
        celdaRotulo.setBorderColor(COLOR_ORO);
        celdaRotulo.setBorderWidth(1.2f);
        celdaRotulo.setPadding(12f);
        celdaRotulo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        caja.addCell(celdaRotulo);

        PdfPCell celdaMonto = new PdfPCell(
                new Paragraph("S/ " + texto(informe.getTotalEstimado()) + " PEN", FUENTE_TOTAL));
        celdaMonto.setBackgroundColor(COLOR_FONDO_SUAVE);
        celdaMonto.setBorder(Rectangle.BOX);
        celdaMonto.setBorderColor(COLOR_ORO);
        celdaMonto.setBorderWidth(1.2f);
        celdaMonto.setPadding(12f);
        celdaMonto.setHorizontalAlignment(Element.ALIGN_RIGHT);
        celdaMonto.setVerticalAlignment(Element.ALIGN_MIDDLE);
        caja.addCell(celdaMonto);

        documento.add(caja);
    }

    private void agregarPie(Document documento, InformeConsolidadoDTO informe) throws DocumentException {
        String tren = informe.getTarifaTren() == null ? "no aplica" : "S/ " + informe.getTarifaTren();
        if (informe.getServicioTren() != null) {
            tren += " (" + informe.getServicioTren() + ")";
        }
        String zona = informe.getCostoZona() == null
                || informe.getCostoZona().signum() <= 0 ? "libre" : "S/ " + informe.getCostoZona();

        Paragraph pie = new Paragraph("Referencia de tarifas: tren " + tren + " · ingreso a la zona "
                + zona + ", ambas por adulto. Generado automáticamente por el sistema del MTC.",
                FUENTE_AVISO);
        pie.setAlignment(Element.ALIGN_CENTER);
        pie.setSpacingBefore(14f);
        documento.add(pie);
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Franja de color a ancho completo, para el filete de cabecera. */
    private PdfPTable filete(Color color, float grosor) throws DocumentException {
        PdfPTable franja = new PdfPTable(1);
        franja.setWidthPercentage(100);

        PdfPCell celda = new PdfPCell();
        celda.setFixedHeight(grosor);
        celda.setBackgroundColor(color);
        celda.setBorder(Rectangle.NO_BORDER);
        franja.addCell(celda);
        return franja;
    }

    private void agregarCelda(PdfPTable tabla, String texto, Color fondo, int alineacion) {
        PdfPCell celda = new PdfPCell(new Paragraph(texto, FUENTE_VALOR));
        celda.setBackgroundColor(fondo);
        // Solo regla inferior: la cuadricula completa encierra la tabla sin
        // aportar nada a la lectura.
        celda.setBorder(Rectangle.BOTTOM);
        celda.setBorderColor(COLOR_LINEA);
        celda.setPadding(7f);
        celda.setHorizontalAlignment(alineacion);
        tabla.addCell(celda);
    }

    private String formatearFecha(InformeConsolidadoDTO informe) {
        return informe.getFechaVisita() == null ? "-" : informe.getFechaVisita().format(FORMATO_FECHA);
    }

    private String texto(Object dato) {
        return dato == null ? "—" : dato.toString();
    }
}
