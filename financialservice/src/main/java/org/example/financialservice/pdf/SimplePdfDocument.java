package org.example.financialservice.pdf;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small, dependency-free builder for the one-page documents ClimateSure issues
 * (policy certificates, payout receipts): a header band, a title, label/value
 * sections and closing notes. Writes PDF 1.4 directly using the standard
 * Helvetica fonts, so no PDF library is needed on the classpath.
 */
public class SimplePdfDocument{
    private static final float PAGE_WIDTH=595f;
    private static final float PAGE_HEIGHT=842f;
    private static final float MARGIN_X=48f;
    private static final float MARGIN_TOP=40f;
    private static final float MARGIN_BOTTOM=48f;
    private static final float CONTENT_WIDTH=PAGE_WIDTH-2*MARGIN_X;
    private static final float LABEL_WIDTH=CONTENT_WIDTH*0.4f;

    private static final float[] BRAND={37/255f,99/255f,235/255f};
    private static final float[] MUTED={107/255f,114/255f,128/255f};
    private static final float[] RULE={229/255f,231/255f,235/255f};
    private static final float[] BLACK={0f,0f,0f};
    private static final float[] WHITE={1f,1f,1f};

    private static final Charset WIN_ANSI=Charset.forName("windows-1252");

    private enum Font{
        REGULAR("F1",HELVETICA_WIDTHS),
        BOLD("F2",HELVETICA_BOLD_WIDTHS),
        ITALIC("F3",HELVETICA_WIDTHS);

        final String resource;
        final int[] widths;

        Font(String resource,int[] widths){
            this.resource=resource;
            this.widths=widths;
        }
    }

    private final String title;
    private final String subtitle;
    private final Map<String,Map<String,String>> sections=new LinkedHashMap<>();
    private final List<String> notes=new ArrayList<>();

    // rendering state
    private final List<StringBuilder> pages=new ArrayList<>();
    private StringBuilder page;
    private float y;

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
        pages.clear();
        newPage();

        // header band
        fillRect(MARGIN_X,y-36,CONTENT_WIDTH,36,BRAND);
        text("ClimateSure Parametric Insurance",MARGIN_X+12,y-23,Font.BOLD,12,WHITE);
        y-=36+30;

        text(title,MARGIN_X,y,Font.BOLD,20,BLACK);
        y-=18;
        if(subtitle!=null&&!subtitle.isBlank()){
            text(subtitle,MARGIN_X,y,Font.REGULAR,10,MUTED);
            y-=12;
        }

        for(Map.Entry<String,Map<String,String>> section:sections.entrySet()){
            ensureSpace(40);
            y-=16;
            text(section.getKey().toUpperCase(Locale.ROOT),MARGIN_X,y,Font.BOLD,9,BRAND);
            y-=8;
            for(Map.Entry<String,String> row:section.getValue().entrySet()){
                String value=row.getValue()==null||row.getValue().isBlank()?"-":row.getValue();
                List<String> labelLines=wrap(row.getKey(),Font.REGULAR,10,LABEL_WIDTH-8);
                List<String> valueLines=wrap(value,Font.BOLD,10,CONTENT_WIDTH-LABEL_WIDTH-4);
                int lines=Math.max(labelLines.size(),valueLines.size());
                float height=lines*12+12;
                ensureSpace(height);
                float baseline=y-15;
                for(int i=0;i<labelLines.size();i++){
                    text(labelLines.get(i),MARGIN_X+2,baseline-i*12,Font.REGULAR,10,MUTED);
                }
                for(int i=0;i<valueLines.size();i++){
                    text(valueLines.get(i),MARGIN_X+LABEL_WIDTH+2,baseline-i*12,Font.BOLD,10,BLACK);
                }
                y-=height;
                line(MARGIN_X,y,MARGIN_X+CONTENT_WIDTH,y,RULE);
            }
        }

        if(!notes.isEmpty()){
            y-=16;
            for(String note:notes){
                for(String lineText:wrap(note,Font.REGULAR,9,CONTENT_WIDTH)){
                    ensureSpace(12);
                    y-=11;
                    text(lineText,MARGIN_X,y,Font.REGULAR,9,MUTED);
                }
                y-=4;
            }
        }

        String footer="This document was generated electronically and is valid without a signature.";
        ensureSpace(30);
        y-=26;
        text(footer,MARGIN_X+(CONTENT_WIDTH-width(footer,Font.ITALIC,8))/2,y,Font.ITALIC,8,MUTED);

        return assemble();
    }

    // ------------------------------------------------------------ drawing

    private void newPage(){
        page=new StringBuilder();
        pages.add(page);
        y=PAGE_HEIGHT-MARGIN_TOP;
    }

    private void ensureSpace(float needed){
        if(y-needed<MARGIN_BOTTOM){
            newPage();
        }
    }

    private void text(String value,float x,float baseline,Font font,float size,float[] color){
        page.append(String.format(Locale.ROOT,"%.3f %.3f %.3f rg BT /%s %.1f Tf %.2f %.2f Td (%s) Tj ET%n",
                color[0],color[1],color[2],font.resource,size,x,baseline,escape(value)));
    }

    private void fillRect(float x,float bottom,float w,float h,float[] color){
        page.append(String.format(Locale.ROOT,"%.3f %.3f %.3f rg %.2f %.2f %.2f %.2f re f%n",
                color[0],color[1],color[2],x,bottom,w,h));
    }

    private void line(float x1,float y1,float x2,float y2,float[] color){
        page.append(String.format(Locale.ROOT,"%.3f %.3f %.3f RG 0.75 w %.2f %.2f m %.2f %.2f l S%n",
                color[0],color[1],color[2],x1,y1,x2,y2));
    }

    // ------------------------------------------------------------ text helpers

    private static List<String> wrap(String value,Font font,float size,float maxWidth){
        List<String> lines=new ArrayList<>();
        for(String paragraph:value.split("\\r?\\n")){
            StringBuilder current=new StringBuilder();
            for(String word:paragraph.split(" ")){
                String candidate=current.isEmpty()?word:current+" "+word;
                if(width(candidate,font,size)<=maxWidth||current.isEmpty()){
                    current.setLength(0);
                    current.append(candidate);
                }else{
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                }
            }
            lines.add(current.toString());
        }
        return lines;
    }

    static float width(String value,Font font,float size){
        float units=0;
        for(byte b:encode(value)){
            int code=b&0xFF;
            units+=code>=32&&code<=126?font.widths[code-32]:556;
        }
        return units*size/1000f;
    }

    private static byte[] encode(String value){
        String safe=value.replace('–','-').replace('—','-')
                .replace('‘','\'').replace('’','\'')
                .replace('“','"').replace('”','"')
                .replace("₹","INR ");
        StringBuilder out=new StringBuilder();
        for(char c:safe.toCharArray()){
            out.append(WIN_ANSI.newEncoder().canEncode(c)?c:'?');
        }
        return out.toString().getBytes(WIN_ANSI);
    }

    /** PDF literal string body, in WinAnsi bytes (kept as ISO-8859-1 chars). */
    private static String escape(String value){
        String latin=new String(encode(value),StandardCharsets.ISO_8859_1);
        StringBuilder out=new StringBuilder();
        for(char c:latin.toCharArray()){
            if(c=='('||c==')'||c=='\\'){
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }

    // ------------------------------------------------------------ file assembly

    private byte[] assemble(){
        List<String> objects=new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add(null); // pages tree, filled in below
        objects.add(font("Helvetica"));
        objects.add(font("Helvetica-Bold"));
        objects.add(font("Helvetica-Oblique"));

        StringBuilder kids=new StringBuilder();
        for(StringBuilder content:pages){
            byte[] stream=content.toString().getBytes(StandardCharsets.ISO_8859_1);
            objects.add("<< /Length "+stream.length+" >>\nstream\n"+content+"endstream");
            int contentId=objects.size();
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842]"
                    +" /Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R >> >>"
                    +" /Contents "+contentId+" 0 R >>");
            kids.append(objects.size()).append(" 0 R ");
        }
        objects.set(1,"<< /Type /Pages /Kids ["+kids.toString().trim()+"] /Count "+pages.size()+" >>");

        ByteArrayOutputStream out=new ByteArrayOutputStream();
        write(out,"%PDF-1.4\n%âãÏÓ\n");
        List<Integer> offsets=new ArrayList<>();
        for(int i=0;i<objects.size();i++){
            offsets.add(out.size());
            write(out,(i+1)+" 0 obj\n"+objects.get(i)+"\nendobj\n");
        }
        int xref=out.size();
        StringBuilder table=new StringBuilder("xref\n0 "+(objects.size()+1)+"\n0000000000 65535 f \n");
        for(int offset:offsets){
            table.append(String.format(Locale.ROOT,"%010d 00000 n \n",offset));
        }
        table.append("trailer\n<< /Size ").append(objects.size()+1).append(" /Root 1 0 R >>\n")
                .append("startxref\n").append(xref).append("\n%%EOF\n");
        write(out,table.toString());
        return out.toByteArray();
    }

    private static String font(String name){
        return "<< /Type /Font /Subtype /Type1 /BaseFont /"+name+" /Encoding /WinAnsiEncoding >>";
    }

    private static void write(ByteArrayOutputStream out,String value){
        byte[] bytes=value.getBytes(StandardCharsets.ISO_8859_1);
        out.write(bytes,0,bytes.length);
    }

    // Standard Helvetica advance widths (1/1000 em) for characters 32..126.
    private static final int[] HELVETICA_WIDTHS={
            278,278,355,556,556,889,667,191,333,333,389,584,278,333,278,278,
            556,556,556,556,556,556,556,556,556,556,278,278,584,584,584,556,
            1015,667,667,722,722,667,611,778,722,278,500,667,556,833,722,778,
            667,778,722,667,611,722,667,944,667,667,611,278,278,278,469,556,
            333,556,556,500,556,556,278,556,556,222,222,500,222,833,556,556,
            556,556,333,500,278,556,500,722,500,500,500,334,260,334,584
    };

    private static final int[] HELVETICA_BOLD_WIDTHS={
            278,333,474,556,556,889,722,238,333,333,389,584,278,333,278,278,
            556,556,556,556,556,556,556,556,556,556,333,333,584,584,584,611,
            975,722,722,722,722,667,611,778,722,278,556,722,611,833,722,778,
            667,778,722,667,611,722,667,944,667,667,611,333,278,333,584,556,
            333,556,611,556,611,556,333,611,611,278,278,556,278,889,611,611,
            611,611,389,556,333,611,556,778,556,556,500,389,280,389,584
    };

    static{
        if(HELVETICA_WIDTHS.length!=95||HELVETICA_BOLD_WIDTHS.length!=95){
            throw new IllegalStateException("Helvetica width tables must cover characters 32..126");
        }
    }
}
