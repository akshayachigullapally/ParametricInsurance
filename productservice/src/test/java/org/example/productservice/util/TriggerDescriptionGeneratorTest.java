package org.example.productservice.util;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class TriggerDescriptionGeneratorTest{

    @Test
    void buildDescription_whenRainfallWith_countOne_shouldBuildSentence(){
        String description=TriggerDescriptionGenerator.buildDescription(
                "RAINFALL",
                "mm",
                new BigDecimal("150.00"),
                "GREATER_THAN_OR_EQUAL",
                "SUM",
                24,
                1
        );

        assertThat(description).isEqualTo(
                "Coverage is triggered when total rainfall reaches or exceeds 150 mm within 24 hours."
        );
    }

    @Test
    void buildDescription_whenWaterLevelMax_shouldBuildSentence(){
        String description=TriggerDescriptionGenerator.buildDescription(
                "WATER_LEVEL",
                "m",
                new BigDecimal("5.00"),
                "GREATER_THAN_OR_EQUAL",
                "MAX",
                6,
                1
        );

        assertThat(description).isEqualTo(
                "Coverage is triggered when the maximum water level reaches or exceeds 5 m within 6 hours."
        );
    }

    @Test
    void buildDescription_whenMinimumCountGreaterThanOne_shouldMentionObservations(){
        String description=TriggerDescriptionGenerator.buildDescription(
                "TEMPERATURE",
                "°C",
                new BigDecimal("35.00"),
                "GREATER_THAN",
                "AVERAGE",
                72,
                3
        );

        assertThat(description).isEqualTo(
                "Coverage is triggered when the average temperature is greater than 35 °C using at least 3 observations within 72 hours."
        );
    }

    @Test
    void buildDescription_whenSingleHourWindow_shouldUseSingular(){
        String description=TriggerDescriptionGenerator.buildDescription(
                "WIND_SPEED",
                "km/h",
                new BigDecimal("90"),
                "GREATER_THAN_OR_EQUAL",
                "MAX",
                1,
                1
        );

        assertThat(description).isEqualTo(
                "Coverage is triggered when the maximum wind speed reaches or exceeds 90 km/h within 1 hour."
        );
    }

    @Test
    void buildDescription_whenNullValues_shouldReturnNull(){
        assertThat(TriggerDescriptionGenerator.buildDescription(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )).isNull();
    }

    @Test
    void toHumanReadable_whenRainfall_shouldBuildShortText(){
        String text=TriggerDescriptionGenerator.toHumanReadable(
                "RAINFALL",
                "mm",
                new BigDecimal("150.00"),
                "GREATER_THAN_OR_EQUAL",
                "SUM",
                24
        );

        assertThat(text).isEqualTo("Total rainfall >= 150 mm within 24 hours");
    }

    @Test
    void toHumanReadable_whenLessThan_shouldUseSymbol(){
        String text=TriggerDescriptionGenerator.toHumanReadable(
                "HUMIDITY",
                "%",
                new BigDecimal("30.00"),
                "LESS_THAN",
                "MIN",
                12
        );

        assertThat(text).isEqualTo("Minimum humidity < 30 % within 12 hours");
    }
}