package org.example.productservice.model;

import java.util.Locale;

public enum MeasurementUnit{
    MM("mm"),
    METER("m"),
    CELSIUS("°C"),
    KM_PER_HOUR("km/h"),
    PERCENTAGE("%");

    private final String display;

    MeasurementUnit(String display){
        this.display=display;
    }

    public String getDisplay(){
        return display;
    }

    public static MeasurementUnit fromText(String value){
        if(value==null||value.isBlank()){
            return null;
        }

        String normalized=value.trim().toUpperCase(Locale.ROOT)
                .replace("KM/H","KM_PER_HOUR")
                .replace("%","PERCENTAGE");

        for(MeasurementUnit unit:values()){
            if(unit.name().equals(normalized)||
                    unit.display.equalsIgnoreCase(value.trim())){
                return unit;
            }
        }

        return null;
    }
}