package org.example.productservice.util;

import org.example.productservice.model.AggregationType;
import org.example.productservice.model.ComparisonOperator;
import org.example.productservice.model.MeasurementType;
import org.example.productservice.model.MeasurementUnit;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Locale;

public final class TriggerDescriptionGenerator{
    private TriggerDescriptionGenerator(){}

    public static String buildDescription(
            String measurementType,
            String measurementUnit,
            BigDecimal thresholdValue,
            String comparisonOperator,
            String aggregationType,
            Integer observationWindowHours,
            Integer minimumObservationCount
    ){
        String measurement=measurementWord(measurementType);
        String unit=unitDisplay(measurementUnit);
        String operator=operatorWord(comparisonOperator);
        String aggregationPhrase=aggregationPhrase(aggregationType);

        if(measurement==null||unit==null||operator==null||
                aggregationPhrase==null||thresholdValue==null||
                observationWindowHours==null){
            return null;
        }

        String hours=observationWindowHours==1
                ?"1 hour"
                :observationWindowHours+" hours";

        if(minimumObservationCount!=null&&minimumObservationCount>1){
            return "Coverage is triggered when "+aggregationPhrase+" "+measurement+
                    " "+operator+" "+thresholdValue.stripTrailingZeros().toPlainString()+
                    " "+unit+" using at least "+minimumObservationCount+
                    " observations within "+hours+".";
        }

        return "Coverage is triggered when "+aggregationPhrase+" "+measurement+
                " "+operator+" "+thresholdValue.stripTrailingZeros().toPlainString()+
                " "+unit+" within "+hours+".";
    }

    public static String toHumanReadable(
            String measurementType,
            String measurementUnit,
            BigDecimal thresholdValue,
            String comparisonOperator,
            String aggregationType,
            Integer observationWindowHours
    ){
        String measurement=humanMeasurementWord(measurementType);
        String unit=unitDisplay(measurementUnit);
        String operator=operatorSymbol(comparisonOperator);
        String aggregation=aggregationHumanWord(aggregationType);

        if(measurement==null||unit==null||operator==null||
                aggregation==null||thresholdValue==null||
                observationWindowHours==null){
            return null;
        }

        return aggregation+" "+measurement+" "+operator+" "+
                thresholdValue.stripTrailingZeros().toPlainString()+" "+unit+
                " within "+observationWindowHours+" hours";
    }

    public static String unitDisplay(String measurementUnit){
        if(!StringUtils.hasText(measurementUnit)){
            return null;
        }

        MeasurementUnit unit=MeasurementUnit.fromText(measurementUnit);
        return unit!=null?unit.getDisplay():measurementUnit.trim();
    }

    private static String measurementWord(String measurementType){
        if(!StringUtils.hasText(measurementType)){
            return null;
        }

        return switch(parse(measurementType,MeasurementType.class)){
            case RAINFALL->"rainfall";
            case WATER_LEVEL->"water level";
            case TEMPERATURE->"temperature";
            case WIND_SPEED->"wind speed";
            case HUMIDITY->"humidity";
            case SOIL_MOISTURE->"soil moisture";
            default->measurementType.toLowerCase(Locale.ROOT);
        };
    }

    private static String humanMeasurementWord(String measurementType){
        if(!StringUtils.hasText(measurementType)){
            return null;
        }

        return switch(parse(measurementType,MeasurementType.class)){
            case RAINFALL->"rainfall";
            case WATER_LEVEL->"water level";
            case TEMPERATURE->"temperature";
            case WIND_SPEED->"wind speed";
            case HUMIDITY->"humidity";
            case SOIL_MOISTURE->"soil moisture";
            default->measurementType.toLowerCase(Locale.ROOT);
        };
    }

    private static String operatorWord(String operator){
        if(!StringUtils.hasText(operator)){
            return null;
        }

        return switch(parse(operator,ComparisonOperator.class)){
            case GREATER_THAN->"is greater than";
            case GREATER_THAN_OR_EQUAL->"reaches or exceeds";
            case LESS_THAN->"falls below";
            case LESS_THAN_OR_EQUAL->"reaches or falls below";
            case EQUAL->"equals";
        };
    }

    private static String operatorSymbol(String operator){
        if(!StringUtils.hasText(operator)){
            return null;
        }

        return switch(parse(operator,ComparisonOperator.class)){
            case GREATER_THAN->">";
            case GREATER_THAN_OR_EQUAL->">=";
            case LESS_THAN->"<";
            case LESS_THAN_OR_EQUAL->"<=";
            case EQUAL->"=";
        };
    }

    private static String aggregationPhrase(String aggregation){
        if(!StringUtils.hasText(aggregation)){
            return null;
        }

        return switch(parse(aggregation,AggregationType.class)){
            case SUM->"total";
            case AVERAGE->"the average";
            case MIN->"the minimum";
            case MAX->"the maximum";
            case LATEST->"the latest";
        };
    }

    private static String aggregationHumanWord(String aggregation){
        if(!StringUtils.hasText(aggregation)){
            return null;
        }

        return switch(parse(aggregation,AggregationType.class)){
            case SUM->"Total";
            case AVERAGE->"Average";
            case MIN->"Minimum";
            case MAX->"Maximum";
            case LATEST->"Latest";
        };
    }

    private static <T extends Enum<T>> T parse(String value,Class<T> enumClass){
        try{
            return Enum.valueOf(enumClass,value.trim().toUpperCase(Locale.ROOT));
        }catch(IllegalArgumentException exception){
            throw new IllegalArgumentException(
                    "Unsupported "+enumClass.getSimpleName()+": "+value
            );
        }
    }
}