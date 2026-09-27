package org.example.productservice.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimplePdfDocumentTest{
    @Test
    void rendersAValidPdfWithTheText(){
        Map<String,String> rows=new LinkedHashMap<>();
        rows.put("Policy number","POL-1");
        rows.put("Description","A long description (with brackets) that should wrap onto more than one line "
                +"because it is much wider than the value column of the table on the page.");

        byte[] pdf=new SimplePdfDocument("Certificate of insurance","Issued today")
                .section("Policy",rows)
                .note("Paid in INR ₹ when the trigger is met.")
                .render();
        String text=new String(pdf,StandardCharsets.ISO_8859_1);

        assertThat(text).startsWith("%PDF-1.4");
        assertThat(text).contains("(Certificate of insurance) Tj");
        assertThat(text).contains("\\(with brackets\\)");
        assertThat(text).contains("INR INR");
        assertThat(text).contains("/Count 1");
        assertThat(text.trim()).endsWith("%%EOF");
        // xref offset points at the xref table
        int startxref=Integer.parseInt(text.substring(text.lastIndexOf("startxref")+10,text.lastIndexOf("%%EOF")).trim());
        assertThat(text.substring(startxref)).startsWith("xref");
    }

    @Test
    void longDocumentsFlowOntoMorePages(){
        Map<String,String> rows=new LinkedHashMap<>();
        for(int i=0;i<80;i++){
            rows.put("Row "+i,"Value "+i);
        }
        String text=new String(new SimplePdfDocument("Long","").section("Rows",rows).render(),
                StandardCharsets.ISO_8859_1);

        assertThat(text).doesNotContain("/Count 1 ");
        assertThat(text).doesNotContain("/Count 1 >>");
    }
}
