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
    // Group first, then look up the one matching row per group: joining the grouped
    // table back to observations made H2 re-run the grouping per row (~20 s at 150k rows).
    private static final String LATEST_PER_ZONE_SQL=
            "SELECT g.location_zone_id, z.zone_code, z.zone_name, g.measurement_type, "
                    +"(SELECT o.measurement_unit FROM observations o "
                    +"  WHERE o.status='VALIDATED' AND o.location_zone_id=g.location_zone_id "
                    +"    AND o.measurement_type=g.measurement_type AND o.observed_at=g.latest "
                    +"  LIMIT 1) AS measurement_unit, "
                    +"(SELECT o.observed_value FROM observations o "
                    +"  WHERE o.status='VALIDATED' AND o.location_zone_id=g.location_zone_id "
                    +"    AND o.measurement_type=g.measurement_type AND o.observed_at=g.latest "
                    +"  LIMIT 1) AS observed_value, "
                    +"g.latest AS observed_at "
                    +"FROM (SELECT location_zone_id, measurement_type, MAX(observed_at) AS latest "
                    +"      FROM observations WHERE status='VALIDATED' "
                    +"      GROUP BY location_zone_id, measurement_type) g "
                    +"LEFT JOIN location_zones z ON z.id=g.location_zone_id "
                    +"ORDER BY g.location_zone_id, g.measurement_type";

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
                .all();
    }
}
