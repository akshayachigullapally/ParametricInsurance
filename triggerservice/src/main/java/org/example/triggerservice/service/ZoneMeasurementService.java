package org.example.triggerservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.triggerservice.dto.ZoneMeasurementResponse;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Live environment readings per zone: the latest VALIDATED observation of every
 * measurement type in every zone (the same data the header measurement panel
 * shows, but for all zones instead of only the most recent one overall).
 */
@Slf4j
@Service
public class ZoneMeasurementService{
    private static final String LATEST_PER_ZONE_SQL=
            "SELECT o.location_zone_id, z.zone_code, z.zone_name, "
                    +"o.measurement_type, o.measurement_unit, o.observed_value, o.observed_at "
                    +"FROM observations o "
                    +"JOIN (SELECT location_zone_id, measurement_type, MAX(observed_at) AS latest "
                    +"      FROM observations WHERE status='VALIDATED' "
                    +"      GROUP BY location_zone_id, measurement_type) l "
                    +"  ON l.location_zone_id=o.location_zone_id "
                    +" AND l.measurement_type=o.measurement_type "
                    +" AND l.latest=o.observed_at "
                    +"LEFT JOIN location_zones z ON z.id=o.location_zone_id "
                    +"WHERE o.status='VALIDATED' "
                    +"ORDER BY o.location_zone_id, o.measurement_type";

    private final DatabaseClient databaseClient;

    public ZoneMeasurementService(DatabaseClient databaseClient){
        this.databaseClient=databaseClient;
    }

    public Flux<ZoneMeasurementResponse> latestPerZone(){
        log.debug("Latest per zone");
        return databaseClient.sql(LATEST_PER_ZONE_SQL)
                .map((row,meta)->ZoneMeasurementResponse.builder()
                        .locationZoneId(row.get("location_zone_id",Long.class))
                        .zoneCode(row.get("zone_code",String.class))
                        .zoneName(row.get("zone_name",String.class))
                        .measurementType(row.get("measurement_type",String.class))
                        .measurementUnit(row.get("measurement_unit",String.class))
                        .currentValue(row.get("observed_value",BigDecimal.class))
                        .observedAt(row.get("observed_at",LocalDateTime.class))
                        .build())
                .all()
                // two readings can share the latest timestamp: keep one per zone and type
                .distinct(reading->reading.getLocationZoneId()+"|"+reading.getMeasurementType());
    }
}
