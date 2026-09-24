package org.example.triggerservice.util;

import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.MeasurementUnit;

public final class MeasurementValidator{
    private MeasurementValidator(){}

    public static void validateMeasurementUnit(
            MeasurementType measurementType,
            MeasurementUnit measurementUnit
    ){
        boolean valid=switch(measurementType){
            case RAINFALL->measurementUnit==MeasurementUnit.MM;
            case TEMPERATURE->measurementUnit==MeasurementUnit.CELSIUS
                    ||measurementUnit==MeasurementUnit.KELVIN;
            case WIND_SPEED->measurementUnit==MeasurementUnit.KM_PER_HOUR
                    ||measurementUnit==MeasurementUnit.METERS_PER_SECOND;
            case HUMIDITY,SOIL_MOISTURE->measurementUnit==MeasurementUnit.PERCENTAGE;
            case RIVER_LEVEL->measurementUnit==MeasurementUnit.METER
                    ||measurementUnit==MeasurementUnit.CM;
            case OTHER->true;
        };

        if(!valid){
            throw new ValidationException(
                    "Measurement unit is not valid for "+measurementType.name());
        }
    }
}
