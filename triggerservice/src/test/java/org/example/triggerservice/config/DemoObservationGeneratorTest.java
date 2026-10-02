package org.example.triggerservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.LocationZoneStatus;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.Observation;
import org.example.triggerservice.model.ObservationSource;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.ObservationRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DemoObservationGeneratorTest{
    private final LocationZoneRepository zones=mock(LocationZoneRepository.class);
    private final ObservationRepository observations=mock(ObservationRepository.class);
    private final DemoObservationGenerator generator=new DemoObservationGenerator(zones,observations,0,3,30);
    private final LocalDateTime now=LocalDateTime.of(2026,10,2,12,0);

    @Test
    void recentAdminReading_isHeld(){
        assertThat(generator.isHeldByAdmin(reading(ObservationSource.MANUAL,now.minusMinutes(10)),now)).isTrue();
    }

    @Test
    void adminReading_isReleasedAfterTheHold(){
        assertThat(generator.isHeldByAdmin(reading(ObservationSource.MANUAL,now.minusMinutes(31)),now)).isFalse();
    }

    @Test
    void feedReadings_areNeverHeld(){
        assertThat(generator.isHeldByAdmin(reading(ObservationSource.MOCK,now.minusMinutes(1)),now)).isFalse();
    }

    @Test
    void backfillBeforeAnAdminReading_isNotHeld(){
        assertThat(generator.isHeldByAdmin(reading(ObservationSource.MANUAL,now.plusHours(2)),now)).isFalse();
    }

    @Test
    void scheduledRun_skipsZonesHeldByAnAdmin(){
        when(zones.findAllByStatusOrderByZoneNameAsc(LocationZoneStatus.ACTIVE))
                .thenReturn(Flux.just(LocationZone.builder().id(1L).build()));
        when(observations.findFirstByLocationZoneIdAndMeasurementTypeOrderByObservedAtDesc(any(),any()))
                .thenReturn(Mono.just(reading(ObservationSource.MANUAL,LocalDateTime.now().minusMinutes(1))));

        generator.generateNextReading();

        verify(observations,never()).save(any());
    }

    private static Observation reading(ObservationSource source,LocalDateTime observedAt){
        return Observation.builder()
                .locationZoneId(1L)
                .measurementType(MeasurementType.RAINFALL)
                .source(source)
                .observedAt(observedAt)
                .build();
    }
}
