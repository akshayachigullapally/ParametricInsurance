package org.example.triggerservice.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditDiffTest{

    @Test
    void missingValuesShowAsDash(){
        AuditDiff diff=AuditDiff.create().field("Postal code",null,"500001");

        assertThat(diff.before()).isEqualTo("Postal code: —");
        assertThat(diff.after()).isEqualTo("Postal code: 500001");
    }
}
