package org.example.triggerservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.example.triggerservice.dto.ZoneMeasurementResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ZoneMeasurementServiceTest{
    @Autowired private DatabaseClient db;
    @Autowired private ZoneMeasurementService service;

    private long zoneId;

    @BeforeEach
    void setUp(){
        db.sql("DELETE FROM observations WHERE observation_reference LIKE 'ZM-TEST-%'").then().block();
        db.sql("DELETE FROM location_zones WHERE zone_code='ZM_TEST'").then().block();
        db.sql("INSERT INTO location_zones(zone_code,zone_name,country,state,district,postal_code,latitude,longitude,"
                        +"radius_km,status,created_by,created_at,updated_by,updated_at) "
                        +"VALUES('ZM_TEST','Map Test Zone','India','Telangana','Hyderabad','500001',17.1,78.1,5,"
                        +"'ACTIVE',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP)")
                .then().block();
        zoneId=db.sql("SELECT id FROM location_zones WHERE zone_code='ZM_TEST'")
                .map(row->row.get("id",Long.class)).one().block();

        LocalDateTime now=LocalDateTime.now();
        insert("ZM-TEST-1","RAINFALL","MM","40",now.minusHours(2),"VALIDATED");
        insert("ZM-TEST-2","RAINFALL","MM","162",now.minusMinutes(5),"VALIDATED");
        insert("ZM-TEST-3","RAINFALL","MM","999",now.minusMinutes(1),"REJECTED");
        insert("ZM-TEST-4","WATER_LEVEL","METER","3.2",now.minusMinutes(10),"VALIDATED");
    }

    private void insert(String ref,String type,String unit,String value,LocalDateTime at,String status){
        db.sql("INSERT INTO observations(observation_reference,location_zone_id,measurement_type,measurement_unit,"
                        +"observed_value,observed_at,source,source_reference,status,received_by,received_at) "
                        +"VALUES(:ref,:zone,:type,:unit,:value,:at,'MANUAL',:ref,:status,1,CURRENT_TIMESTAMP)")
                .bind("ref",ref).bind("zone",zoneId).bind("type",type).bind("unit",unit)
                .bind("value",new BigDecimal(value)).bind("at",at).bind("status",status)
                .then().block();
    }

    @Test
    void returnsTheLatestValidatedReadingPerZoneAndType(){
        List<ZoneMeasurementResponse> readings=service.latestPerZone()
                .filter(reading->reading.getLocationZoneId()==zoneId)
                .collectList().block();

        assertThat(readings).hasSize(2);
        ZoneMeasurementResponse rain=readings.stream()
                .filter(reading->reading.getMeasurementType().equals("RAINFALL"))
                .findFirst().orElseThrow();
        assertThat(rain.getCurrentValue()).isEqualByComparingTo("162");
        assertThat(rain.getZoneName()).isEqualTo("Map Test Zone");
    }
}
