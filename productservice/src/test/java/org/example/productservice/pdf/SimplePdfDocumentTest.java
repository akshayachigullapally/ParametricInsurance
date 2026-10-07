package org.example.productservice.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimplePdfDocumentTest{

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
