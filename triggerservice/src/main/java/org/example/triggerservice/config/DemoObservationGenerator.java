package org.example.triggerservice.config;

import lombok.extern.slf4j.Slf4j;
import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.LocationZoneStatus;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.MeasurementUnit;
import org.example.triggerservice.model.Observation;
import org.example.triggerservice.model.ObservationSource;
import java.time.Duration;
import org.example.triggerservice.model.ObservationStatus;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.ObservationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@ConditionalOnProperty(
        name="demo.feed.enabled",
        havingValue="true",
        matchIfMissing=false
)
public class DemoObservationGenerator implements CommandLineRunner{
    private static final Long SYSTEM_USER_ID=1L;

    private static final Map<MeasurementType,BigDecimal[]> VALUE_RANGES=
            new EnumMap<>(MeasurementType.class);

    static{
        VALUE_RANGES.put(MeasurementType.RAINFALL,
                new BigDecimal[]{new BigDecimal("0.00"),new BigDecimal("0.15")});
        VALUE_RANGES.put(MeasurementType.RIVER_LEVEL,
                new BigDecimal[]{new BigDecimal("1.50"),new BigDecimal("4.00")});
        VALUE_RANGES.put(MeasurementType.TEMPERATURE,
                new BigDecimal[]{new BigDecimal("18.0"),new BigDecimal("34.0")});
        VALUE_RANGES.put(MeasurementType.WIND_SPEED,
                new BigDecimal[]{new BigDecimal("5.0"),new BigDecimal("30.0")});
        VALUE_RANGES.put(MeasurementType.HUMIDITY,
                new BigDecimal[]{new BigDecimal("45.0"),new BigDecimal("80.0")});
        VALUE_RANGES.put(MeasurementType.SOIL_MOISTURE,
                new BigDecimal[]{new BigDecimal("15.0"),new BigDecimal("45.0")});
    }

    private final LocationZoneRepository locationZoneRepository;
    private final ObservationRepository observationRepository;
    private final int backfillHours;
    private final int retentionDays;
    private final Duration manualHold;

    public DemoObservationGenerator(
            LocationZoneRepository locationZoneRepository,
            ObservationRepository observationRepository,
            @Value("${demo.feed.backfill-hours:24}") int backfillHours,
            @Value("${demo.feed.retention-days:3}") int retentionDays,
            @Value("${demo.feed.manual-hold-minutes:30}") int manualHoldMinutes
    ){
        this.locationZoneRepository=locationZoneRepository;
        this.observationRepository=observationRepository;
        this.backfillHours=Math.max(backfillHours,0);
        this.retentionDays=Math.max(retentionDays,1);
        this.manualHold=Duration.ofMinutes(Math.max(manualHoldMinutes,0));
    }

    @Override
    public void run(String... args){
        if(backfillHours<=0){
            return;
        }

        backfillObservations()
                .doOnComplete(()->log.info(
                        "Demo observation backfill completed for the last {} hour(s)",
                        backfillHours))
                .doOnError(error->log.error(
                        "Demo observation backfill failed: {}",
                        error.getMessage(),error))
                .subscribe();
    }

    @Scheduled(fixedDelayString="${demo.feed.interval-ms:60000}")
    public void generateNextReading(){
        generateReadings(LocalDateTime.now())
                .doOnComplete(()->log.debug(
                        "Generated demo observations at {}",LocalDateTime.now()))
                .doOnError(error->log.warn(
                        "Demo observation generation failed: {}",
                        error.getMessage()))
                .subscribe();
    }

    // The feed adds ~440k rows a day; without trimming, every observation query slows down.
    @Scheduled(
            initialDelayString="${demo.feed.retention-initial-delay-ms:120000}",
            fixedDelayString="${demo.feed.retention-interval-ms:3600000}")
    public void deleteOldReadings(){
        observationRepository
                .deleteMockObservedBefore(LocalDateTime.now().minusDays(retentionDays))
                .doOnNext(deleted->log.info(
                        "Deleted {} demo observation(s) older than {} day(s)",
                        deleted,retentionDays))
                .doOnError(error->log.warn(
                        "Demo observation cleanup failed: {}",
                        error.getMessage()))
                .subscribe();
    }

    private Flux<Observation> backfillObservations(){
        LocalDateTime now=LocalDateTime.now();

        return Flux.range(0,backfillHours)
                .flatMap(hourOffset->generateReadings(
                        now.minusHours(backfillHours-hourOffset)));
    }

    private Flux<Observation> generateReadings(LocalDateTime observedAt){
        return activeZones()
                .flatMap(zone->Flux.fromIterable(VALUE_RANGES.keySet())
                        .flatMap(type->saveReading(zone,type,observedAt)));
    }

    private Flux<LocationZone> activeZones(){
        return locationZoneRepository
                .findAllByStatusOrderByZoneNameAsc(LocationZoneStatus.ACTIVE);
    }

    private Mono<Observation> saveReading(
            LocationZone zone,
            MeasurementType measurementType,
            LocalDateTime observedAt
    ){
        return observationRepository
                .findFirstByLocationZoneIdAndMeasurementTypeOrderByObservedAtDesc(zone.getId(),measurementType)
                .map(latest->isHeldByAdmin(latest,observedAt))
                .defaultIfEmpty(false)
                .flatMap(held->held?Mono.<Observation>empty():saveRandomReading(zone,measurementType,observedAt));
    }

    /** A reading an admin set by hand is kept as the live value for a while instead of being overwritten. */
    boolean isHeldByAdmin(Observation latest,LocalDateTime observedAt){
        return latest.getSource()==ObservationSource.MANUAL
                &&latest.getObservedAt()!=null
                &&!latest.getObservedAt().isAfter(observedAt)
                &&latest.getObservedAt().isAfter(observedAt.minus(manualHold));
    }

    private Mono<Observation> saveRandomReading(
            LocationZone zone,
            MeasurementType measurementType,
            LocalDateTime observedAt
    ){
        Observation observation=Observation.builder()
                .observationReference(generateReference())
                .locationZoneId(zone.getId())
                .measurementType(measurementType)
                .measurementUnit(unitFor(measurementType))
                .observedValue(randomValue(measurementType))
                .observedAt(observedAt)
                .source(ObservationSource.MOCK)
                .sourceReference("MOCK-"+UUID.randomUUID()
                        .toString().replace("-","")
                        .substring(0,12).toUpperCase(Locale.ROOT))
                .status(ObservationStatus.VALIDATED)
                .receivedBy(SYSTEM_USER_ID)
                .receivedAt(LocalDateTime.now())
                .build();

        return observationRepository.save(observation);
    }

    private BigDecimal randomValue(MeasurementType measurementType){
        BigDecimal[] range=VALUE_RANGES.get(measurementType);
        double min=range[0].doubleValue();
        double max=range[1].doubleValue();
        int scale=Math.max(range[0].scale(),range[1].scale());

        return BigDecimal.valueOf(
                        min+ThreadLocalRandom.current().nextDouble()*(max-min))
                .setScale(scale, RoundingMode.HALF_UP);
    }

    private MeasurementUnit unitFor(MeasurementType measurementType){
        return switch(measurementType){
            case RAINFALL->MeasurementUnit.MM;
            case RIVER_LEVEL->MeasurementUnit.METER;
            case TEMPERATURE->MeasurementUnit.CELSIUS;
            case WIND_SPEED->MeasurementUnit.KM_PER_HOUR;
            case HUMIDITY,SOIL_MOISTURE->MeasurementUnit.PERCENTAGE;
            case OTHER->MeasurementUnit.OTHER;
        };
    }

    private String generateReference(){
        return "OBS-"+LocalDateTime.now().getYear()+"-"+UUID.randomUUID()
                .toString().replace("-","")
                .substring(0,10).toUpperCase(Locale.ROOT);
    }
}