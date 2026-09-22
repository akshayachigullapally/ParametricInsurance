package org.example.triggerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.MeasurementUnit;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasurementTypeInfo{
    private MeasurementType measurementType;
    private MeasurementUnit measurementUnit;
    private String displayName;
}