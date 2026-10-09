package org.example.triggerservice.flow;

import org.example.triggerservice.client.ClaimsServiceClient;
import org.example.triggerservice.client.ProductTriggerClient;
import org.example.triggerservice.dto.ClaimCreationResponse;
import org.example.triggerservice.dto.ActivePolicyForTriggerResponse;
import org.example.triggerservice.dto.InternalProductConfig;
import org.example.triggerservice.model.TriggerDefinition;
import org.example.triggerservice.model.TriggerDefinitionStatus;
import org.example.triggerservice.repository.TriggerDefinitionRepository;
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
    @Autowired
    private TriggerDefinitionRepository definitions;

    private WebTestClient admin;

    @BeforeEach
    void setUp() throws InterruptedException{
        admin=client("ROLE_ADMIN");
        when(productClient.hasActivePoliciesForZone(anyLong())).thenReturn(Mono.just(false));
        when(productClient.getProduct(anyLong())).thenAnswer(call->Mono.just(InternalProductConfig.builder()
                .productId(call.getArgument(0)).productCode("PRODUCT-"+call.getArgument(0)).status("ACTIVE")
                .measurementType("TEMPERATURE").measurementUnit("CELSIUS").thresholdValue(new BigDecimal("45"))
                .comparisonOperator("GREATER_THAN_OR_EQUAL").observationWindowHours(24).build()));
        when(productClient.getActivePolicies(any(),any())).thenReturn(Flux.empty());
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
        for(int i=0;i<100&&(definition("HYD_RAIN_150MM_24H")==null||definitions.count().block()<4);i++){
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

    private TriggerDefinition definition(String code){
        return definitions.findByDefinitionCodeIgnoreCase(code).block();
    }

    private List<TriggerDefinition> productDefinitions(long productId){
        return definitions.findAllByProductIdAndStatusNot(productId,TriggerDefinitionStatus.ARCHIVED)
                .collectList().block();
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
        TriggerDefinition rain=definition("HYD_RAIN_150MM_24H");
        assertThat(map(HttpMethod.GET,"/api/triggers/definitions/"+rain.getId(),null))
                .containsEntry("productId",1).containsEntry("perilCode","EXCESS_RAINFALL");
        assertThat(productDefinitions(2)).allSatisfy(d->assertThat(d.getMeasurementType().name()).isEqualTo("RIVER_LEVEL"));
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

        send(HttpMethod.DELETE,"/api/triggers/zones/"+zoneId,null).expectStatus().is2xxSuccessful();
        send(HttpMethod.DELETE,"/api/triggers/perils/"+perilId,null).expectStatus().is2xxSuccessful();

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
        assertThat(productDefinitions(7)).hasSize(zones)
                .allSatisfy(d->assertThat(d.getMeasurementType().name()).isEqualTo("TEMPERATURE"));

        map(HttpMethod.PUT,"/internal/triggers/products/7/definition",sync(7,"ACTIVE",new BigDecimal("47")));
        assertThat(productDefinitions(7))
                .hasSize(zones).allSatisfy(d->assertThat(d.getThresholdValue().intValue()).isEqualTo(47));

        map(HttpMethod.PUT,"/internal/triggers/products/7/definition",sync(7,"INACTIVE",new BigDecimal("47")));
        assertThat(productDefinitions(7))
                .allSatisfy(d->assertThat(d.getStatus()).isNotEqualTo(TriggerDefinitionStatus.ACTIVE));

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

        assertThat(list(HttpMethod.GET,"/internal/triggers/measurements/current",null)).isNotEmpty();
        long zoneId=id(zone("SURAT_CENTRAL"),"id");
        map(HttpMethod.PUT,"/api/triggers/current-measurements",
                Map.of("productId",8,"locationZoneId",zoneId,"currentValue",46,"idempotencyKey","flow-test-reading-1"));
        assertThat(list(HttpMethod.GET,"/api/triggers/current-measurements",null)).isNotEmpty();
        assertThat(list(HttpMethod.GET,"/api/triggers/measurements/zones",null)).isNotEmpty();
        assertThat(result).containsKeys("triggeredProducts","matchedPolicyCount","holdMinutes");
    }

    @Test
    void currentMeasurementCreatesVerifiedEventClaimsAndIsMarkedProcessed(){
        long zoneId=id(zone("HYDERABAD_CENTRAL"),"id");
        ActivePolicyForTriggerResponse.TriggerSnapshot snapshot=new ActivePolicyForTriggerResponse.TriggerSnapshot();
        snapshot.setThresholdValue(new BigDecimal("45"));
        snapshot.setComparisonOperator("GREATER_THAN_OR_EQUAL");
        ActivePolicyForTriggerResponse paid=new ActivePolicyForTriggerResponse();
        paid.setPolicyId(11L);
        paid.setPolicyNumber("POL-11");
        paid.setCustomerId(3L);
        paid.setSumInsured(new BigDecimal("50000"));
        paid.setPremiumPaid(true);
        paid.setTriggerSnapshot(snapshot);
        when(productClient.getActivePolicies(9L,zoneId)).thenReturn(Flux.just(paid));

        Map<String,Object> body=Map.of("productId",9,"locationZoneId",zoneId,"currentValue",47,"idempotencyKey","flow-claim-1");
        Map<String,Object> result=map(HttpMethod.PUT,"/api/triggers/current-measurements",body);
        assertThat(result).containsEntry("matched",true).containsEntry("triggerEventStatus","VERIFIED")
                .containsEntry("claimsCreatedCount",1);
        long eventId=id(result,"triggerEventId");
        verify(claimsClient,atLeastOnce()).createClaimsFromTriggerEvent(any());

        // The same request again returns the first event instead of creating another one.
        assertThat(id(map(HttpMethod.PUT,"/api/triggers/current-measurements",body),"triggerEventId")).isEqualTo(eventId);

        assertThat(list(HttpMethod.GET,"/api/triggers/events",null)).anyMatch(e->((Number)e.get("id")).longValue()==eventId);
        assertThat(map(HttpMethod.GET,"/api/triggers/events/"+eventId,null)).containsEntry("status","VERIFIED");
        send(HttpMethod.PUT,"/internal/triggers/events/"+eventId+"/processed",null).expectStatus().isOk();
        assertThat(map(HttpMethod.GET,"/api/triggers/events/"+eventId,null)).containsEntry("status","PROCESSED");
        send(HttpMethod.PUT,"/internal/triggers/events/"+eventId+"/processed",null).expectStatus().isEqualTo(HttpStatus.CONFLICT);
        send(HttpMethod.GET,"/api/triggers/events/999999",null).expectStatus().isNotFound();
    }
}
