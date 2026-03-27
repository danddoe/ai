Country → Region/State → City → Location (Address / Site)

country_id (PK)
name
iso_code


CREATE TABLE region (
region_id UNIQUEIDENTIFIER PRIMARY KEY,
country_id UNIQUEIDENTIFIER NOT NULL,
parent_region_id UNIQUEIDENTIFIER NULL,
name VARCHAR(255) NOT NULL,
type VARCHAR(50) NOT NULL, -- province, state, county, district, etc.
code VARCHAR(50) NULL,
level INT NOT NULL, -- 1=top, 2=sub, 3=sub-sub
CONSTRAINT fk_region_country FOREIGN KEY (country_id)
REFERENCES country(country_id),
CONSTRAINT fk_region_parent FOREIGN KEY (parent_region_id)
REFERENCES region(region_id)
);


CREATE TABLE city (
city_id UNIQUEIDENTIFIER PRIMARY KEY,
region_id UNIQUEIDENTIFIER NOT NULL,
name VARCHAR(255) NOT NULL,
postal_code VARCHAR(20),
latitude DECIMAL(9,6),
longitude DECIMAL(9,6),
CONSTRAINT fk_city_region FOREIGN KEY (region_id)
REFERENCES region(region_id)
);

CREATE TABLE location (
location_id UNIQUEIDENTIFIER PRIMARY KEY,
city_id UNIQUEIDENTIFIER NULL,
address_line_1 VARCHAR(255),
address_line_2 VARCHAR(255),
postal_code VARCHAR(20),
CONSTRAINT fk_location_city FOREIGN KEY (city_id)
REFERENCES city(city_id)
);


location_id (PK)
city_id (FK)
address_line_1
address_line_2
postal_code

Pro Tip (Important for your ERP-style system)

Since you're building dynamic/ERP-like structures:

Treat geography as master data
Cache frequently used locations
Consider adding:
timezone
is_active
external_code (for integrations)


What this means for your database design

👉 Treat region as a generic abstraction

region
-------
region_id
country_id
name
type   -- e.g. 'province', 'state', 'county'