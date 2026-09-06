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
 */
@Component
public class GeneradorPdf {

    // OpenPDF 2.x (com.lowagie.text.Font) usa la familia como constante int
    // (Font.HELVETICA), no el enum Font.FontFamily.HELVETICA de la serie 3.x.
    private static final Font FUENTE_TITULO = new Font(Font.HELVETICA, 18, Font.BOLD);
    private static final Font FUENTE_SUBTITULO = new Font(Font.HELVETICA, 11, Font.ITALIC, Color.GRAY);
    private static final Font FUENTE_ETIQUETA = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font FUENTE_VALOR = new Font(Font.HELVETICA, 10);
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] generar(InformeConsolidadoDTO informe) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 40, 40, 50, 40);

        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();

            documento.add(new Paragraph("Asesor Turístico Ferroviario y Peatonal - MTC", FUENTE_TITULO));
            documento.add(new Paragraph("Informe consolidado de visita  ·  Código: " + informe.getCodigo(), FUENTE_SUBTITULO));
            documento.add(Chunk.NEWLINE);

            PdfPTable tabla = new PdfPTable(2);
            tabla.setWidthPercentage(100);
            tabla.setWidths(new float[]{1.3f, 2f});

            agregarFila(tabla, "Fecha de visita", formatearFecha(informe));
            agregarFila(tabla, "Estación de origen", informe.getEstacionOrigen());
            agregarFila(tabla, "Zona turística", informe.getZonaDestino());

            if (informe.getRuta() != null) {
                agregarFila(tabla, "Distancia (ida y vuelta)", valor(informe.getRuta().getDistanciaKm(), " km"));
                agregarFila(tabla, "Tiempo estimado", valor(informe.getRuta().getTiempoEstimadoMin(), " min"));
                agregarFila(tabla, "Dificultad", informe.getRuta().getDificultad());
            }

            agregarFila(tabla, "Clima previsto",
                    valor(informe.getTemperaturaMinimaC(), "") + " - " + valor(informe.getTemperaturaMaximaC(), "°C, ")
                            + valor(informe.getProbabilidadLluvia(), "% lluvia")
                            + (informe.getEstadoClima() != null ? " (" + informe.getEstadoClima() + ")" : ""));
            agregarFila(tabla, "Tarifa del tren (adulto)", valor(informe.getTarifaTren(), null));
            agregarFila(tabla, "Ingreso a la zona (adulto)", valor(informe.getCostoZona(), null));
            agregarFila(tabla, "Personas", informe.getTotalPersonas());
            agregarFila(tabla, "Total estimado", valor(informe.getTotalEstimado(), null));

            documento.add(tabla);
            documento.add(Chunk.NEWLINE);

            agregarDetalleDelGrupo(documento, informe);
            documento.add(new Paragraph("Generado automáticamente por el sistema del MTC.", FUENTE_SUBTITULO));

            documento.close();
        } catch (DocumentException ex) {
            throw new IllegalStateException("No se pudo generar el PDF del informe consolidado", ex);
        }

        return salida.toByteArray();
    }

    /**
     * RF-18: desglose por edad. Deja ver de donde sale el total cuando viaja
     * mas de una persona, que es justo lo que el turista quiere comprobar.
     */
    private void agregarDetalleDelGrupo(Document documento, InformeConsolidadoDTO informe)
            throws DocumentException {
        if (informe.getVisitantes() == null || informe.getVisitantes().isEmpty()) {
            return;
        }

        documento.add(new Paragraph("Detalle por visitante", FUENTE_ETIQUETA));
        documento.add(Chunk.NEWLINE);

        PdfPTable detalle = new PdfPTable(5);
        detalle.setWidthPercentage(100);
        detalle.setWidths(new float[]{0.8f, 0.8f, 1.4f, 1.4f, 1f});

        for (String cabecera : new String[]{"Edad", "Personas", "Tarifa tren", "Entrada zona", "Subtotal"}) {
            PdfPCell celda = new PdfPCell(new Paragraph(cabecera, FUENTE_ETIQUETA));
            celda.setBorderColor(Color.LIGHT_GRAY);
            celda.setPadding(5);
            detalle.addCell(celda);
        }

        for (VisitanteDTO visitante : informe.getVisitantes()) {
            agregarCelda(detalle, visitante.getEdad() + " años");
            agregarCelda(detalle, String.valueOf(visitante.getCantidad()));
            agregarCelda(detalle, (visitante.getCategoriaTren() == null
                    ? "Sin tren" : visitante.getCategoriaTren())
                    + "  ·  S/ " + visitante.getSubtotalTren());
            agregarCelda(detalle, visitante.getCategoriaZona()
                    + "  ·  S/ " + visitante.getSubtotalZona());
            agregarCelda(detalle, "S/ " + visitante.getSubtotal());
        }

        documento.add(detalle);
        documento.add(Chunk.NEWLINE);
    }

    private void agregarCelda(PdfPTable tabla, String texto) {
        PdfPCell celda = new PdfPCell(new Paragraph(texto, FUENTE_VALOR));
        celda.setBorderColor(Color.LIGHT_GRAY);
        celda.setPadding(5);
        tabla.addCell(celda);
    }

    private void agregarFila(PdfPTable tabla, String etiqueta, Object valor) {
        PdfPCell celdaEtiqueta = new PdfPCell(new Paragraph(etiqueta, FUENTE_ETIQUETA));
        celdaEtiqueta.setBorderColor(Color.LIGHT_GRAY);
        celdaEtiqueta.setPadding(6);

        PdfPCell celdaValor = new PdfPCell(new Paragraph(valor == null ? "-" : valor.toString(), FUENTE_VALOR));
        celdaValor.setBorderColor(Color.LIGHT_GRAY);
        celdaValor.setPadding(6);

        tabla.addCell(celdaEtiqueta);
        tabla.addCell(celdaValor);
    }

    private String formatearFecha(InformeConsolidadoDTO informe) {
        return informe.getFechaVisita() == null ? "-" : informe.getFechaVisita().format(FORMATO_FECHA);
    }

    private String valor(Object dato, String sufijo) {
        if (dato == null) {
            return "";
        }
        return dato + (sufijo == null ? "" : sufijo);
    }
}
