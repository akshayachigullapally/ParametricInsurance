package org.example.triggerservice.flow;

import org.example.triggerservice.client.ClaimsServiceClient;
import org.example.triggerservice.client.InternalNotificationClient;
import org.example.triggerservice.client.ProductTriggerClient;
import org.example.triggerservice.dto.ClaimCreationResponse;
import org.example.triggerservice.dto.EligiblePolicyResponse;
import org.example.triggerservice.dto.InternalProductConfig;
import org.example.triggerservice.service.NotificationServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end flows through the real routers, handlers, services and an in-memory database
 * (seeded by TriggerDataLoader). Only the calls to other microservices are mocked.
 */
@SpringBootTest(properties={
        "spring.r2dbc.url=r2dbc:h2:mem:///flowdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "trigger.seed-data.enabled=true"})
@ActiveProfiles("test")
class TriggerServiceFlowTest{

    @Autowired
    private ApplicationContext context;
    @MockitoBean
    private ProductTriggerClient productClient;
    @MockitoBean
    private ClaimsServiceClient claimsClient;
    @MockitoBean
    private InternalNotificationClient internalNotificationClient;
    @MockitoBean
    private NotificationServiceClient notificationClient;

    private WebTestClient admin;

    @BeforeEach
    void setUp() throws InterruptedException{
        admin=client("ROLE_ADMIN");
        when(notificationClient.sendNotification(any())).thenReturn(Mono.empty());
        when(internalNotificationClient.createInternalNotification(any())).thenReturn(Mono.empty());
        when(productClient.notifyVerifiedTriggerEvent(any(),any())).thenReturn(Mono.empty());
        when(productClient.hasActivePoliciesForZone(anyLong())).thenReturn(Mono.just(false));
        when(productClient.getProduct(anyLong())).thenAnswer(call->Mono.just(InternalProductConfig.builder()
                .productId(call.getArgument(0)).productCode("PRODUCT-"+call.getArgument(0)).status("ACTIVE")
                .measurementType("TEMPERATURE").measurementUnit("CELSIUS").thresholdValue(new BigDecimal("45"))
                .comparisonOperator("GREATER_THAN_OR_EQUAL").observationWindowHours(24).build()));
        when(productClient.getActivePolicies(any(),any())).thenReturn(Flux.empty());
        when(productClient.getEligiblePoliciesForEvent(any(),any(),any(),any(),any())).thenReturn(Flux.just(
                EligiblePolicyResponse.builder().policyId(11L).policyNumber("POL-11").customerId(3L).productId(1L)
                        .sumInsured(new BigDecimal("50000")).status("ACTIVE").premiumPaid(true).build()));
        when(claimsClient.createClaimsFromTriggerEvent(any())).thenReturn(Mono.just(
                ClaimCreationResponse.builder().claimsCreated(1).claimsFailed(0).results(List.of()).build()));
        waitForSeedData();
    }

    private WebTestClient client(String role){
        return WebTestClient.bindToApplicationContext(context).configureClient()
                .responseTimeout(Duration.ofSeconds(30))
                .codecs(codecs->codecs.defaultCodecs().maxInMemorySize(16*1024*1024))
                .defaultHeader("X-Gateway-Key","test-gateway-key")
                .defaultHeader("X-Authenticated-User-Id","1")
                .defaultHeader("X-Authenticated-User","admin@gmail.com")
                .defaultHeader("X-Authenticated-Role",role)
                .build();
    }

    private void waitForSeedData() throws InterruptedException{
        for(int i=0;i<100&&list(HttpMethod.GET,"/api/triggers/definitions",null).size()<4;i++){
            Thread.sleep(100);
        }
    }

    private WebTestClient.ResponseSpec send(HttpMethod method,String path,Object body){
        WebTestClient.RequestBodySpec spec=admin.method(method).uri(path);
        return body==null?spec.exchange():spec.bodyValue(body).exchange();
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> map(HttpMethod method,String path,Object body){
        return ok(send(method,path,body).expectBody(Map.class).returnResult(),method,path);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> list(HttpMethod method,String path,Object body){
        return ok(send(method,path,body).expectBody(List.class).returnResult(),method,path);
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private <T> T ok(org.springframework.test.web.reactive.server.EntityExchangeResult result,HttpMethod method,String path){
        assertThat(result.getStatus().is2xxSuccessful())
                .as("%s %s -> %s %s",method,path,result.getStatus(),new String(result.getResponseBodyContent()==null?new byte[0]:result.getResponseBodyContent()))
                .isTrue();
        return (T)result.getResponseBody();
    }

    private static long id(Map<String,Object> body,String field){
        return ((Number)body.get(field)).longValue();
    }

    private Map<String,Object> zone(String code){
        return list(HttpMethod.GET,"/api/triggers/zones",null).stream()
                .filter(z->code.equals(z.get("zoneCode"))).findFirst().orElseThrow();
    }

    private Map<String,Object> definition(String code){
        return list(HttpMethod.GET,"/api/triggers/definitions",null).stream()
                .filter(d->code.equals(d.get("definitionCode"))).findFirst().orElseThrow();
    }

    private static Map<String,Object> sync(long productId,String status,BigDecimal threshold){
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("productId",productId);
        body.put("productCode","HEAT-"+productId);
        body.put("productName","Heatwave Cover");
        body.put("productStatus",status);
        body.put("perilCode","EXTREME_TEMPERATURE");
        body.put("measurementType","TEMPERATURE");
        body.put("measurementUnit","CELSIUS");
        body.put("thresholdValue",threshold);
        body.put("comparisonOperator","GREATER_THAN_OR_EQUAL");
        body.put("aggregationType","MAX");
        body.put("observationWindowHours",24);
        body.put("minimumObservationCount",1);
        body.put("updatedBy",1);
        return body;
    }

    @Test
    void seededReferenceDataIsServed(){
        assertThat(list(HttpMethod.GET,"/api/triggers/perils",null)).extracting(p->p.get("perilCode"))
                .contains("EXCESS_RAINFALL","DROUGHT","FLOOD","EXTREME_TEMPERATURE","HIGH_WIND","CYCLONE");
        Map<String,Object> hyderabad=zone("HYDERABAD_CENTRAL");
        long zoneId=id(hyderabad,"id");
        assertThat(map(HttpMethod.GET,"/api/triggers/zones/"+zoneId,null)).containsEntry("zoneCode","HYDERABAD_CENTRAL");
        assertThat(list(HttpMethod.GET,"/api/triggers/zones/active",null)).isNotEmpty();
        assertThat(list(HttpMethod.GET,"/internal/triggers/zones/active",null)).isNotEmpty();
        assertThat(map(HttpMethod.GET,"/internal/triggers/zones/"+zoneId,null)).containsEntry("zoneCode","HYDERABAD_CENTRAL");
        assertThat(list(HttpMethod.GET,"/api/triggers/measurements/types",null)).extracting(t->t.get("measurementType"))
                .contains("RAINFALL","TEMPERATURE");
        Map<String,Object> rain=definition("P1_NAGPUR_CENTRAL_RAINFALL");
        assertThat(map(HttpMethod.GET,"/api/triggers/definitions/"+rain.get("id"),null)).containsEntry("productId",1);
        assertThat(list(HttpMethod.GET,"/api/triggers/definitions/active",null)).isNotEmpty();
        assertThat(list(HttpMethod.GET,"/api/triggers/definitions/zone/"+zoneId,null)).isNotEmpty();
        assertThat(list(HttpMethod.GET,"/api/triggers/definitions/peril/"+rain.get("perilId"),null)).isNotEmpty();
        assertThat(list(HttpMethod.GET,"/api/triggers/definitions/product/2",null))
                .allSatisfy(d->assertThat(d.get("measurementType")).isEqualTo("RIVER_LEVEL"));
    }

    @Test
    void adminManagesPerilsZonesAndDefinitions(){
        Map<String,Object> peril=map(HttpMethod.POST,"/api/triggers/perils",
                Map.of("perilCode","HAIL_TEST","perilName","Hail","description","Hailstones larger than 2 cm"));
        long perilId=id(peril,"id");
        assertThat(map(HttpMethod.PUT,"/api/triggers/perils/"+perilId,
                Map.of("perilCode","HAIL_TEST","perilName","Hailstorm","description","Large hail"))).containsEntry("perilName","Hailstorm");
        assertThat(map(HttpMethod.GET,"/api/triggers/perils/"+perilId,null)).containsEntry("perilCode","HAIL_TEST");
        assertThat(map(HttpMethod.PUT,"/api/triggers/perils/"+perilId+"/status",Map.of("status","INACTIVE"))).containsEntry("status","INACTIVE");
        assertThat(map(HttpMethod.PUT,"/api/triggers/perils/"+perilId+"/status",Map.of("status","ACTIVE"))).containsEntry("status","ACTIVE");

        Map<String,Object> zoneBody=new LinkedHashMap<>(Map.of("zoneCode","TEST_ZONE","zoneName","Test Zone","country","India",
                "state","Telangana","district","Medak","latitude",17.5,"longitude",78.2,"radiusKm",10));
        long zoneId=id(map(HttpMethod.POST,"/api/triggers/zones",zoneBody),"id");
        zoneBody.put("zoneName","Test Zone North");
        assertThat(map(HttpMethod.PUT,"/api/triggers/zones/"+zoneId,zoneBody)).containsEntry("zoneName","Test Zone North");
        assertThat(map(HttpMethod.PUT,"/api/triggers/zones/"+zoneId+"/alert",Map.of("active",true,"message","Heavy rain warning")))
                .containsEntry("weatherAlert",true);
        assertThat(map(HttpMethod.PUT,"/api/triggers/zones/"+zoneId+"/alert",Map.of("active",false))).containsEntry("weatherAlert",false);
        map(HttpMethod.PUT,"/api/triggers/zones/"+zoneId+"/exposure-cap",Map.of("maxExposure",5000000));

        Map<String,Object> definitionBody=new LinkedHashMap<>();
        definitionBody.put("definitionCode","TEST_HAIL_DEF");
        definitionBody.put("definitionName","Test hail trigger");
        definitionBody.put("description","Hail over 30 mm");
        definitionBody.put("perilId",perilId);
        definitionBody.put("locationZoneId",zoneId);
        definitionBody.put("productId",900);
        definitionBody.put("measurementType","RAINFALL");
        definitionBody.put("measurementUnit","MM");
        definitionBody.put("aggregationType","SUM");
        definitionBody.put("comparisonOperator","GREATER_THAN_OR_EQUAL");
        definitionBody.put("thresholdValue",30);
        definitionBody.put("observationWindowHours",6);
        definitionBody.put("minimumObservationCount",1);
        definitionBody.put("effectiveFrom",LocalDate.now().toString());
        long definitionId=id(map(HttpMethod.POST,"/api/triggers/definitions",definitionBody),"id");
        definitionBody.put("thresholdValue",35);
        assertThat(((Number)map(HttpMethod.PUT,"/api/triggers/definitions/"+definitionId,definitionBody).get("thresholdValue")).intValue()).isEqualTo(35);
        assertThat(map(HttpMethod.PUT,"/api/triggers/definitions/"+definitionId+"/status",Map.of("status","ACTIVE"))).containsEntry("status","ACTIVE");
        assertThat(list(HttpMethod.GET,"/api/triggers/definitions/product/900",null)).hasSize(1);

        send(HttpMethod.DELETE,"/api/triggers/definitions/"+definitionId,null).expectStatus().isEqualTo(HttpStatus.CONFLICT);
        assertThat(map(HttpMethod.PUT,"/api/triggers/definitions/"+definitionId+"/status",Map.of("status","INACTIVE"))).containsEntry("status","INACTIVE");
        send(HttpMethod.DELETE,"/api/triggers/definitions/"+definitionId,null).expectStatus().is2xxSuccessful();
        send(HttpMethod.DELETE,"/api/triggers/zones/"+zoneId,null).expectStatus().is2xxSuccessful();
        send(HttpMethod.DELETE,"/api/triggers/perils/"+perilId,null).expectStatus().is2xxSuccessful();
        send(HttpMethod.GET,"/api/triggers/definitions/"+definitionId,null).expectStatus().isNotFound();

        // Archived items are listed separately and can be restored (they come back INACTIVE).
        assertThat(list(HttpMethod.GET,"/api/triggers/perils",null)).noneMatch(p->p.get("id").equals((int)perilId));
        assertThat(list(HttpMethod.GET,"/api/triggers/perils/archived",null)).anyMatch(p->p.get("id").equals((int)perilId));
        assertThat(list(HttpMethod.GET,"/api/triggers/zones/archived",null)).anyMatch(z->z.get("id").equals((int)zoneId));
        assertThat(map(HttpMethod.PUT,"/api/triggers/perils/"+perilId+"/restore",null)).containsEntry("status","INACTIVE");
        assertThat(map(HttpMethod.PUT,"/api/triggers/zones/"+zoneId+"/restore",null)).containsEntry("status","INACTIVE");
        assertThat(list(HttpMethod.GET,"/api/triggers/perils",null)).anyMatch(p->p.get("id").equals((int)perilId));
        assertThat(list(HttpMethod.GET,"/api/triggers/zones",null)).anyMatch(z->z.get("id").equals((int)zoneId));
        send(HttpMethod.PUT,"/api/triggers/perils/"+perilId+"/restore",null).expectStatus().isEqualTo(HttpStatus.CONFLICT);
        send(HttpMethod.PUT,"/api/triggers/zones/"+zoneId+"/restore",null).expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidRequestsAreRejected(){
        send(HttpMethod.POST,"/api/triggers/perils",Map.of("perilCode","")).expectStatus().isBadRequest();
        send(HttpMethod.GET,"/api/triggers/zones/999999",null).expectStatus().isNotFound();
        send(HttpMethod.GET,"/api/triggers/definitions/abc",null).expectStatus().isBadRequest();
        send(HttpMethod.PUT,"/api/triggers/measurements/current",Map.of("measurementType","RAINFALL","value",-5))
                .expectStatus().isBadRequest();
        WebTestClient.bindToApplicationContext(context).build().get().uri("/api/triggers/zones").exchange()
                .expectStatus().isForbidden();
        WebTestClient.bindToApplicationContext(context).build().get().uri("/api/triggers/zones")
                .header("X-Gateway-Key","test-gateway-key").exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void productSyncKeepsOneDefinitionPerActiveZone(){
        int zones=list(HttpMethod.GET,"/internal/triggers/zones/active",null).size();

        map(HttpMethod.PUT,"/internal/triggers/products/7/definition",sync(7,"ACTIVE",new BigDecimal("45")));
        List<Map<String,Object>> created=list(HttpMethod.GET,"/api/triggers/definitions/product/7",null);
        assertThat(created).hasSize(zones).allSatisfy(d->assertThat(d.get("measurementType")).isEqualTo("TEMPERATURE"));

        map(HttpMethod.PUT,"/internal/triggers/products/7/definition",sync(7,"ACTIVE",new BigDecimal("47")));
        assertThat(list(HttpMethod.GET,"/api/triggers/definitions/product/7",null))
                .hasSize(zones).allSatisfy(d->assertThat(((Number)d.get("thresholdValue")).intValue()).isEqualTo(47));

        map(HttpMethod.PUT,"/internal/triggers/products/7/definition",sync(7,"INACTIVE",new BigDecimal("47")));
        assertThat(list(HttpMethod.GET,"/api/triggers/definitions/product/7",null))
                .allSatisfy(d->assertThat(d.get("status")).isNotEqualTo("ACTIVE"));

        Map<String,Object> bad=sync(7,"ACTIVE",new BigDecimal("47"));
        bad.put("aggregationType","MEDIAN");
        send(HttpMethod.PUT,"/internal/triggers/products/7/definition",bad).expectStatus().isBadRequest();
    }

    @Test
    void manualReadingBecomesTheCurrentValueAndEvaluatesProducts(){
        map(HttpMethod.PUT,"/internal/triggers/products/8/definition",sync(8,"ACTIVE",new BigDecimal("45")));

        Map<String,Object> result=map(HttpMethod.PUT,"/api/triggers/measurements/current",
                Map.of("measurementType","TEMPERATURE","value",48,"productIds",List.of(8)));
        assertThat(((Number)result.get("value")).intValue()).isEqualTo(48);
        assertThat(((Number)result.get("zoneCount")).intValue()).isPositive();

        assertThat(list(HttpMethod.GET,"/api/triggers/measurements/current",null))
                .anySatisfy(m->assertThat(m.get("measurementType")).isEqualTo("TEMPERATURE"));
        assertThat(list(HttpMethod.GET,"/internal/triggers/measurements/current",null)).isNotEmpty();
        long zoneId=id(zone("SURAT_CENTRAL"),"id");
        map(HttpMethod.PUT,"/api/triggers/current-measurements",
                Map.of("productId",8,"locationZoneId",zoneId,"currentValue",46,"idempotencyKey","flow-test-reading-1"));
        assertThat(list(HttpMethod.GET,"/api/triggers/current-measurements",null)).isNotEmpty();
        assertThat(list(HttpMethod.GET,"/api/triggers/measurements/zones",null)).isNotEmpty();
        assertThat(result).containsKeys("triggeredProducts","matchedPolicyCount","holdMinutes");
    }

    @Test
    void simulatedEventsGoThroughReviewAndClaims(){
        List<Map<String,Object>> simulated=list(HttpMethod.POST,"/api/triggers/demo/simulate",Map.of("productId",1));
        assertThat(simulated).isNotEmpty();

        List<Map<String,Object>> events=list(HttpMethod.GET,"/api/triggers/events/product/1",null);
        assertThat(events).hasSizeGreaterThanOrEqualTo(2);
        Map<String,Object> first=events.get(0);
        long eventId=id(first,"id");
        assertThat(map(HttpMethod.GET,"/api/triggers/events/"+eventId,null)).containsEntry("id",(int)eventId);
        assertThat(list(HttpMethod.GET,"/api/triggers/events/status/"+first.get("status"),null)).isNotEmpty();
        assertThat(list(HttpMethod.GET,"/api/triggers/events/definition/"+first.get("triggerDefinitionId"),null)).isNotEmpty();
        assertThat(list(HttpMethod.GET,"/api/triggers/events/zone/"+first.get("locationZoneId"),null)).isNotEmpty();
        assertThat(list(HttpMethod.GET,"/api/triggers/events",null)).isNotEmpty();

        Map<String,Object> notes=Map.of("reviewNotes","Checked against the rain gauge");
        long otherId=id(events.get(1),"id");
        send(HttpMethod.PUT,"/api/triggers/events/"+eventId+"/start-validation",null);
        send(HttpMethod.PUT,"/api/triggers/events/"+eventId+"/verify",notes);
        send(HttpMethod.PUT,"/api/triggers/events/"+eventId+"/process",null);
        send(HttpMethod.PUT,"/internal/triggers/events/"+eventId+"/processed",null);
        send(HttpMethod.PUT,"/api/triggers/events/"+otherId+"/start-validation",null);
        send(HttpMethod.PUT,"/api/triggers/events/"+otherId+"/reject",notes);

        assertThat(map(HttpMethod.GET,"/api/triggers/events/"+otherId,null).get("status")).isEqualTo("REJECTED");
        assertThat(map(HttpMethod.GET,"/api/triggers/events/"+eventId,null).get("status")).isNotEqualTo("REJECTED");
    }

    @Test
    void demoObservationRunsTheWholePipeline(){
        Map<String,Object> definition=definition("P2_KOCHI_CENTRAL_RIVER_LEVEL");
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("productId",2);
        body.put("triggerDefinitionId",definition.get("id"));
        body.put("locationZoneId",definition.get("locationZoneId"));
        body.put("measurementType","RIVER_LEVEL");
        body.put("measurementUnit","METER");
        body.put("observedValue",2.5);
        body.put("observedAt",LocalDateTime.now().minusMinutes(5).toString());
        body.put("sourceReference","demo-gauge-1");
        body.put("validateImmediately",true);
        body.put("evaluateImmediately",true);
        body.put("autoVerifyMatchedEvent",true);
        body.put("createAutomaticClaims",true);

        Map<String,Object> result=map(HttpMethod.POST,"/api/triggers/demo/observations",body);
        assertThat(result.toString()).contains("NOT_MATCHED");
        assertThat(list(HttpMethod.GET,"/api/triggers/events/definition/"+definition.get("id"),null)).isEmpty();

        body.put("measurementUnit","MM");
        send(HttpMethod.POST,"/api/triggers/demo/observations",body).expectStatus().isBadRequest();
        body.put("measurementUnit","METER");
        body.put("productId",1);
        send(HttpMethod.POST,"/api/triggers/demo/observations",body).expectStatus().isBadRequest();

        // Above the 5 m threshold: one event is created, verified and claimed.
        body.put("productId",2);
        body.put("observedValue",6.5);
        body.put("sourceReference","demo-gauge-2");
        body.put("observedAt",LocalDateTime.now().minusMinutes(1).toString());
        Map<String,Object> matched=map(HttpMethod.POST,"/api/triggers/demo/observations",body);
        assertThat(matched.toString()).contains("MATCHED").doesNotContain("NOT_MATCHED");
        assertThat(list(HttpMethod.GET,"/api/triggers/events/definition/"+definition.get("id"),null)).hasSize(1);
        verify(claimsClient,atLeastOnce()).createClaimsFromTriggerEvent(any());
    }

    @Test
    void observationsAreRecordedValidatedAndRejected(){
        long zoneId=id(zone("PUNE_CENTRAL"),"id");
        Map<String,Object> reading=new LinkedHashMap<>(Map.of("locationZoneId",zoneId,"measurementType","RAINFALL",
                "measurementUnit","MM","observedValue",12.5,"observedAt",LocalDateTime.now().minusMinutes(30).toString(),"source","MANUAL"));
        long first=id(map(HttpMethod.POST,"/api/triggers/observations",reading),"id");
        reading.put("observedValue",14);
        long second=id(map(HttpMethod.POST,"/api/triggers/observations",reading),"id");
        reading.put("observedValue",16);
        map(HttpMethod.POST,"/api/triggers/observations",reading);

        assertThat(map(HttpMethod.GET,"/api/triggers/observations/"+first,null)).containsEntry("locationZoneId",(int)zoneId);
        assertThat(list(HttpMethod.GET,"/api/triggers/observations/zone/"+zoneId,null)).hasSizeGreaterThanOrEqualTo(3);
        assertThat(list(HttpMethod.GET,"/api/triggers/observations/measurement/RAINFALL",null)).isNotEmpty();
        assertThat(list(HttpMethod.GET,"/api/triggers/observations",null)).isNotEmpty();
        assertThat(list(HttpMethod.GET,"/api/triggers/observations/validation/received",null)).hasSizeGreaterThanOrEqualTo(3);

        assertThat(map(HttpMethod.PUT,"/api/triggers/observations/validation/"+first+"/validate",null)).containsEntry("status","VALIDATED");
        assertThat(map(HttpMethod.PUT,"/api/triggers/observations/validation/"+second+"/reject",Map.of("reason","Gauge was blocked")))
                .containsEntry("status","REJECTED");
        assertThat(map(HttpMethod.POST,"/api/triggers/observations/validation/validate-all",null).get("message").toString())
                .startsWith("Validated");

        reading.put("source","WEATHER_API");
        send(HttpMethod.POST,"/api/triggers/observations",reading).expectStatus().isBadRequest();
    }

    @Test
    void definitionsCanBeEvaluatedAndMonitorRunsAreRecorded(){
        Map<String,Object> definition=definition("P1_JAIPUR_CENTRAL_RAINFALL");
        send(HttpMethod.POST,"/api/triggers/definitions/"+definition.get("id")+"/evaluate",
                Map.of("evaluationEnd",LocalDateTime.now().toString())).expectStatus().is2xxSuccessful();
        List<Map<String,Object>> evaluations=list(HttpMethod.GET,"/api/triggers/evaluations/definition/"+definition.get("id"),null);
        assertThat(evaluations).isNotEmpty();
        assertThat(map(HttpMethod.GET,"/api/triggers/evaluations/"+evaluations.get(0).get("id"),null)).isNotEmpty();
        assertThat(list(HttpMethod.GET,"/api/triggers/evaluations",null)).isNotEmpty();

        Map<String,Object> run=map(HttpMethod.POST,"/api/triggers/monitor-runs/scan",null);
        assertThat(run).containsEntry("status","COMPLETED");
        assertThat(map(HttpMethod.GET,"/api/triggers/monitor-runs/"+run.get("id"),null)).containsEntry("status","COMPLETED");
        assertThat(list(HttpMethod.GET,"/api/triggers/monitor-runs",null)).isNotEmpty();
    }
}
