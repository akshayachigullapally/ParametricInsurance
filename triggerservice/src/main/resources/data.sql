-- Runs on every startup: insert only when the zone is missing so data persists across restarts.
INSERT INTO location_zones(
    zone_code,zone_name,country,state,district,postal_code,
    latitude,longitude,radius_km,status,
    created_by,created_at,updated_by,updated_at
)
SELECT
          'HYDERABAD_CENTRAL','Hyderabad Central Zone','India',
          'Telangana','Hyderabad','500001',
          17.3850000,78.4867000,25.00,'ACTIVE',
          1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM location_zones WHERE zone_code='HYDERABAD_CENTRAL');

-- Runs on every startup: insert only when the zone is missing so data persists across restarts.
INSERT INTO location_zones(
    zone_code,zone_name,country,state,district,postal_code,
    latitude,longitude,radius_km,status,
    created_by,created_at,updated_by,updated_at
)
SELECT
          'MUMBAI_CENTRAL','Mumbai Central Zone','India',
          'Maharashtra','Mumbai','400001',
          18.9388000,72.8354000,30.00,'ACTIVE',
          1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM location_zones WHERE zone_code='MUMBAI_CENTRAL');

-- Runs on every startup: insert only when the zone is missing so data persists across restarts.
INSERT INTO location_zones(
    zone_code,zone_name,country,state,district,postal_code,
    latitude,longitude,radius_km,status,
    created_by,created_at,updated_by,updated_at
)
SELECT
          'PUNE_TEST','Pune Test Zone','India',
          'Maharashtra','Pune','411001',
          18.5204000,73.8567000,20.00,'INACTIVE',
          1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM location_zones WHERE zone_code='PUNE_TEST');