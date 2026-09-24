package org.example.productservice.model;

import java.util.Locale;

public enum MeasurementUnit{
    MM("mm"),
    CM("cm"),
    METER("m"),
    CELSIUS("°C"),
    KELVIN("K"),
    KM_PER_HOUR("km/h"),
    METERS_PER_SECOND("m/s"),
    PERCENTAGE("%"),
    OTHER("other");

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
                .replace("M/S","METERS_PER_SECOND")
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