INSERT INTO countries (country_code, name, alpha3, numeric_code) VALUES
    ('US', 'United States of America', 'USA', '840'),
    ('GB', 'United Kingdom', 'GBR', '826'),
    ('DE', 'Germany', 'DEU', '276'),
    ('FR', 'France', 'FRA', '250'),
    ('CA', 'Canada', 'CAN', '124')
ON CONFLICT (country_code) DO NOTHING;
