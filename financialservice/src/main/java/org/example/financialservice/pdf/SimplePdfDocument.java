package org.example.financialservice.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small builder for the one-page documents CityShield issues
 * (policy certificates, payout receipts): a header band, a title,
 * one or more label/value sections and closing notes.
 */
public class SimplePdfDocument{
    private static final Color BRAND=new Color(37,99,235);
    private static final Color MUTED=new Color(107,114,128);
    private static final Color RULE=new Color(229,231,235);

    private final String title;
    private final String subtitle;
    private final Map<String,Map<String,String>> sections=new LinkedHashMap<>();
    private final List<String> notes=new ArrayList<>();

    public SimplePdfDocument(String title,String subtitle){
        this.title=title;
        this.subtitle=subtitle;
    }

    public SimplePdfDocument section(String heading,Map<String,String> rows){
        sections.put(heading,rows);
        return this;
    }

    public SimplePdfDocument note(String note){
        notes.add(note);
        return this;
    }

    public byte[] render(){
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        Document document=new Document(PageSize.A4,48,48,40,48);
        PdfWriter.getInstance(document,out);
        document.open();

        PdfPTable band=new PdfPTable(1);
        band.setWidthPercentage(100);
        PdfPCell brand=new PdfPCell(new Phrase("CityShield Parametric Insurance",
                new Font(Font.HELVETICA,12,Font.BOLD,Color.WHITE)));
        brand.setBackgroundColor(BRAND);
        brand.setBorder(Rectangle.NO_BORDER);
        brand.setPadding(10);
        band.addCell(brand);
        document.add(band);

        Paragraph heading=new Paragraph(title,new Font(Font.HELVETICA,20,Font.BOLD));
        heading.setSpacingBefore(18);
        document.add(heading);
        if(subtitle!=null&&!subtitle.isBlank()){
            document.add(new Paragraph(subtitle,new Font(Font.HELVETICA,10,Font.NORMAL,MUTED)));
        }

        for(Map.Entry<String,Map<String,String>> section:sections.entrySet()){
            Paragraph sectionTitle=new Paragraph(section.getKey().toUpperCase(),
                    new Font(Font.HELVETICA,9,Font.BOLD,BRAND));
            sectionTitle.setSpacingBefore(16);
            sectionTitle.setSpacingAfter(6);
            document.add(sectionTitle);

            PdfPTable table=new PdfPTable(new float[]{2f,3f});
            table.setWidthPercentage(100);
            for(Map.Entry<String,String> row:section.getValue().entrySet()){
                table.addCell(cell(row.getKey(),new Font(Font.HELVETICA,10,Font.NORMAL,MUTED)));
                table.addCell(cell(row.getValue()==null||row.getValue().isBlank()?"-":row.getValue(),
                        new Font(Font.HELVETICA,10,Font.BOLD)));
            }
            document.add(table);
        }

        if(!notes.isEmpty()){
            Paragraph spacer=new Paragraph(" ");
            spacer.setSpacingBefore(8);
            document.add(spacer);
            for(String note:notes){
                Paragraph paragraph=new Paragraph(note,new Font(Font.HELVETICA,9,Font.NORMAL,MUTED));
                paragraph.setSpacingAfter(4);
                document.add(paragraph);
            }
        }

        Paragraph footer=new Paragraph(new Chunk("This document was generated electronically and is valid without a signature.",
                new Font(Font.HELVETICA,8,Font.ITALIC,MUTED)));
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(24);
        document.add(footer);

        document.close();
        return out.toByteArray();
    }

    private static PdfPCell cell(String text,Font font){
        PdfPCell cell=new PdfPCell(new Phrase(text,font));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(RULE);
        cell.setPaddingTop(6);
        cell.setPaddingBottom(6);
        return cell;
    }
}
