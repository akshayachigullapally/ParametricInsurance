package org.example.triggerservice.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditDiffTest{
    @Test
    void listsOnlyChangedFieldsInOrder(){
        Map<String,Object> before=new LinkedHashMap<>();
        before.put("Name","Hyderabad");
        before.put("Threshold",new BigDecimal("150.00"));
        before.put("Radius",new BigDecimal("25"));
        Map<String,Object> after=new LinkedHashMap<>();
        after.put("Name","Hyderabad Central");
        after.put("Threshold",new BigDecimal("200"));
        after.put("Radius",new BigDecimal("25.00"));

        AuditDiff diff=AuditDiff.of(before,after);

        assertThat(diff.before()).isEqualTo("Name: Hyderabad; Threshold: 150");
        assertThat(diff.after()).isEqualTo("Name: Hyderabad Central; Threshold: 200");
    }

    @Test
    void noChangesGivesNullValues(){
        AuditDiff diff=AuditDiff.create().field("Status","ACTIVE","ACTIVE");

        assertThat(diff.isEmpty()).isTrue();
        assertThat(diff.before()).isNull();
        assertThat(diff.after()).isNull();
    }

    @Test
    void missingValuesShowAsDash(){
        AuditDiff diff=AuditDiff.create().field("Postal code",null,"500001");

        assertThat(diff.before()).isEqualTo("Postal code: —");
        assertThat(diff.after()).isEqualTo("Postal code: 500001");
    }
}
