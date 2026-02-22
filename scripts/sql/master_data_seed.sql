-- Generated from src/main/resources/seed/master-data.json
-- Purpose: seed master data on fresh/manual setup without Java seeders
BEGIN;

-- 1) Departments
INSERT INTO department (name, code, active) VALUES
  ('Hematology', 'HEM', TRUE),
  ('Biochemistry', 'BIO', TRUE),
  ('Serology', 'SER', TRUE),
  ('Thyroid', 'THY', TRUE),
  ('Urine', 'URI', TRUE),
  ('Microbiology', 'MIC', TRUE)
ON CONFLICT (name) DO UPDATE
SET code = EXCLUDED.code,
    active = EXCLUDED.active;

-- 2) Categories
WITH seed_category(name, department_name, description, active) AS (
  VALUES
    ('CBC / Hemogram', 'Hematology', 'Complete blood count analytes', TRUE),
    ('Differential (Percent)', 'Hematology', 'WBC differential percentages', TRUE),
    ('Differential (Absolute)', 'Hematology', 'WBC differential absolute counts', TRUE),
    ('Platelets', 'Hematology', 'Platelet count', TRUE),
    ('ESR', 'Hematology', 'Erythrocyte sedimentation rate', TRUE),
    ('Blood Grouping', 'Hematology', 'ABO/Rh', TRUE),
    ('Glucose', 'Biochemistry', 'Glucose metabolism', TRUE),
    ('Renal Function', 'Biochemistry', 'Renal function tests', TRUE),
    ('Liver Function', 'Biochemistry', 'Liver enzymes and bilirubin', TRUE),
    ('Lipids', 'Biochemistry', 'Lipid profile', TRUE),
    ('Proteins', 'Biochemistry', 'Total protein and albumin', TRUE),
    ('Electrolytes', 'Biochemistry', 'Electrolytes', TRUE),
    ('Hepatitis', 'Serology', 'Hepatitis screening', TRUE),
    ('Infectious', 'Serology', 'General infectious screening', TRUE),
    ('Dengue', 'Serology', 'Dengue screening', TRUE),
    ('Typhoid', 'Serology', 'Typhoid screening', TRUE),
    ('Thyroid Function', 'Thyroid', 'Thyroid hormones', TRUE),
    ('Urinalysis', 'Urine', 'Routine urinalysis', TRUE),
    ('Physical', 'Urine', 'Physical examination', TRUE),
    ('Chemical', 'Urine', 'Chemical examination', TRUE),
    ('Microscopic', 'Urine', 'Microscopic examination', TRUE),
    ('Pregnancy', 'Urine', 'Pregnancy testing', TRUE),
    ('Culture & Sensitivity', 'Microbiology', 'Cultures', TRUE)
)
INSERT INTO test_categories (name, department_id, description, is_active)
SELECT sc.name, d.id, sc.description, sc.active
FROM seed_category sc
JOIN department d ON lower(d.name) = lower(sc.department_name)
ON CONFLICT (name, department_id) DO UPDATE
SET description = EXCLUDED.description,
    is_active = EXCLUDED.is_active;

-- 3) Inventory
INSERT INTO inventory_items (item_name, unit, current_stock, min_threshold, average_cost, active, version) VALUES
  ('Purple Top Tube (EDTA)', 'pcs', 500, 50, 0, TRUE, 0),
  ('Glucose Strip', 'pcs', 200, 20, 0, TRUE, 0),
  ('Alcohol Swab', 'pcs', 1000, 100, 0, TRUE, 0)
ON CONFLICT (item_name) DO UPDATE
SET unit = EXCLUDED.unit,
    current_stock = EXCLUDED.current_stock,
    min_threshold = EXCLUDED.min_threshold,
    average_cost = EXCLUDED.average_cost,
    active = EXCLUDED.active,
    version = COALESCE(inventory_items.version, 0);

-- 4) Tests (upsert by short_code first, otherwise by exact test_name)
DO $$
DECLARE
    rec RECORD;
    v_test_id BIGINT;
BEGIN
    CREATE TEMP TABLE _seed_test_rows (
        test_name TEXT,
        short_code TEXT,
        department_name TEXT,
        category_name TEXT,
        unit TEXT,
        price NUMERIC,
        min_range NUMERIC,
        max_range NUMERIC,
        active BOOLEAN
    ) ON COMMIT DROP;

    INSERT INTO _seed_test_rows (test_name, short_code, department_name, category_name, unit, price, min_range, max_range, active) VALUES
        ('Complete Blood Count (CBC)', 'CBC', 'Hematology', 'CBC / Hemogram', NULL, 650, NULL, NULL, TRUE),
        ('White Blood Cell Count', 'WBC', 'Hematology', 'CBC / Hemogram', '10^9/L', 150, NULL, NULL, TRUE),
        ('Red Blood Cell Count', 'RBC', 'Hematology', 'CBC / Hemogram', '10^12/L', 150, NULL, NULL, TRUE),
        ('Hemoglobin', 'HB', 'Hematology', 'CBC / Hemogram', 'g/L', 150, NULL, NULL, TRUE),
        ('Hematocrit (PCV)', 'HCT', 'Hematology', 'CBC / Hemogram', 'L/L', 150, NULL, NULL, TRUE),
        ('MCV', 'MCV', 'Hematology', 'CBC / Hemogram', 'fL', 150, NULL, NULL, TRUE),
        ('MCH', 'MCH', 'Hematology', 'CBC / Hemogram', 'pg', 150, NULL, NULL, TRUE),
        ('MCHC', 'MCHC', 'Hematology', 'CBC / Hemogram', 'g/L', 150, NULL, NULL, TRUE),
        ('RDW-CV', 'RDW', 'Hematology', 'CBC / Hemogram', '%', 150, NULL, NULL, TRUE),
        ('Neutrophils %', 'NEUTP', 'Hematology', 'Differential (Percent)', '%', 120, NULL, NULL, TRUE),
        ('Lymphocytes %', 'LYMPHP', 'Hematology', 'Differential (Percent)', '%', 120, NULL, NULL, TRUE),
        ('Monocytes %', 'MONOP', 'Hematology', 'Differential (Percent)', '%', 120, NULL, NULL, TRUE),
        ('Eosinophils %', 'EOSP', 'Hematology', 'Differential (Percent)', '%', 120, NULL, NULL, TRUE),
        ('Basophils %', 'BASOP', 'Hematology', 'Differential (Percent)', '%', 120, NULL, NULL, TRUE),
        ('Neutrophils (Absolute)', 'NEUT', 'Hematology', 'Differential (Absolute)', '10^9/L', 120, NULL, NULL, TRUE),
        ('Lymphocytes (Absolute)', 'LYMPH', 'Hematology', 'Differential (Absolute)', '10^9/L', 120, NULL, NULL, TRUE),
        ('Monocytes (Absolute)', 'MONO', 'Hematology', 'Differential (Absolute)', '10^9/L', 120, NULL, NULL, TRUE),
        ('Eosinophils (Absolute)', 'EOS', 'Hematology', 'Differential (Absolute)', '10^9/L', 120, NULL, NULL, TRUE),
        ('Basophils (Absolute)', 'BASO', 'Hematology', 'Differential (Absolute)', '10^9/L', 120, NULL, NULL, TRUE),
        ('Platelet Count', 'PLT', 'Hematology', 'Platelets', '10^9/L', 200, NULL, NULL, TRUE),
        ('MPV (Mean Platelet Volume)', 'MPV', 'Hematology', 'Platelets', 'fL', 120, NULL, NULL, TRUE),
        ('PDW', 'PDW', 'Hematology', 'Platelets', '%', 120, NULL, NULL, TRUE),
        ('Plateletcrit (PCT)', 'PCT', 'Hematology', 'Platelets', '%', 120, NULL, NULL, TRUE),
        ('ESR (Erythrocyte Sedimentation Rate)', 'ESR', 'Hematology', 'ESR', 'mm/h', 200, NULL, NULL, TRUE),
        ('Blood Group & Rh Factor', 'BGRH', 'Hematology', 'Blood Grouping', NULL, 250, NULL, NULL, TRUE),
        ('Fasting Plasma Glucose', 'GLU-F', 'Biochemistry', 'Glucose', 'mmol/L', 150, NULL, NULL, TRUE),
        ('Random Glucose', 'GLU-R', 'Biochemistry', 'Glucose', 'mmol/L', 150, NULL, NULL, TRUE),
        ('Urea', 'UREA', 'Biochemistry', 'Renal Function', 'mmol/L', 250, NULL, NULL, TRUE),
        ('Creatinine', 'CREAT', 'Biochemistry', 'Renal Function', 'umol/L', 250, NULL, NULL, TRUE),
        ('Uric Acid', 'URIC', 'Biochemistry', 'Renal Function', 'umol/L', 250, NULL, NULL, TRUE),
        ('Total Cholesterol', 'CHOL', 'Biochemistry', 'Lipids', 'mmol/L', 250, NULL, NULL, TRUE),
        ('Triglycerides', 'TRIG', 'Biochemistry', 'Lipids', 'mmol/L', 250, NULL, NULL, TRUE),
        ('HDL Cholesterol', 'HDL', 'Biochemistry', 'Lipids', 'mmol/L', 250, NULL, NULL, TRUE),
        ('LDL Cholesterol', 'LDL', 'Biochemistry', 'Lipids', 'mmol/L', 250, NULL, NULL, TRUE),
        ('Total Bilirubin', 'TBIL', 'Biochemistry', 'Liver Function', 'umol/L', 250, NULL, NULL, TRUE),
        ('ALT (SGPT)', 'ALT', 'Biochemistry', 'Liver Function', 'U/L', 250, NULL, NULL, TRUE),
        ('AST (SGOT)', 'AST', 'Biochemistry', 'Liver Function', 'U/L', 250, NULL, NULL, TRUE),
        ('Alkaline Phosphatase', 'ALP', 'Biochemistry', 'Liver Function', 'U/L', 250, NULL, NULL, TRUE),
        ('Total Protein', 'TP', 'Biochemistry', 'Proteins', 'g/L', 250, NULL, NULL, TRUE),
        ('Albumin', 'ALB', 'Biochemistry', 'Proteins', 'g/L', 250, NULL, NULL, TRUE),
        ('Sodium', 'NA', 'Biochemistry', 'Electrolytes', 'mmol/L', 250, NULL, NULL, TRUE),
        ('Potassium', 'K', 'Biochemistry', 'Electrolytes', 'mmol/L', 250, NULL, NULL, TRUE),
        ('Chloride', 'CL', 'Biochemistry', 'Electrolytes', 'mmol/L', 250, NULL, NULL, TRUE),
        ('Calcium', 'CA', 'Biochemistry', 'Electrolytes', 'mmol/L', 250, NULL, NULL, TRUE),
        ('TSH', 'TSH', 'Thyroid', 'Thyroid Function', 'mIU/L', 500, NULL, NULL, TRUE),
        ('Free T4', 'FT4', 'Thyroid', 'Thyroid Function', 'pmol/L', 500, NULL, NULL, TRUE),
        ('Free T3', 'FT3', 'Thyroid', 'Thyroid Function', 'pmol/L', 500, NULL, NULL, TRUE),
        ('Total T4', 'T4', 'Thyroid', 'Thyroid Function', 'nmol/L', 400, NULL, NULL, TRUE),
        ('Total T3', 'T3', 'Thyroid', 'Thyroid Function', 'nmol/L', 400, NULL, NULL, TRUE),
        ('HBsAg', 'HBSAG', 'Serology', 'Hepatitis', NULL, 400, NULL, NULL, TRUE),
        ('Anti-HCV', 'HCV', 'Serology', 'Hepatitis', NULL, 500, NULL, NULL, TRUE),
        ('HIV I & II', 'HIV', 'Serology', 'Infectious', NULL, 500, NULL, NULL, TRUE),
        ('VDRL', 'VDRL', 'Serology', 'Infectious', NULL, 300, NULL, NULL, TRUE),
        ('Typhidot', 'TYPHI', 'Serology', 'Typhoid', NULL, 600, NULL, NULL, TRUE),
        ('Dengue NS1', 'DEN-NS1', 'Serology', 'Dengue', NULL, 800, NULL, NULL, TRUE),
        ('Dengue IgM', 'DEN-IGM', 'Serology', 'Dengue', NULL, 800, NULL, NULL, TRUE),
        ('Dengue IgG', 'DEN-IGG', 'Serology', 'Dengue', NULL, 800, NULL, NULL, TRUE),
        ('Color', 'UR-COLOR', 'Urine', 'Physical', NULL, 10, NULL, NULL, TRUE),
        ('Appearance / Clarity', 'UR-APPR', 'Urine', 'Physical', NULL, 10, NULL, NULL, TRUE),
        ('Odor', 'UR-ODOR', 'Urine', 'Physical', NULL, 10, NULL, NULL, TRUE),
        ('Volume (24-hr)', 'UR-VOL', 'Urine', 'Physical', 'mL/day', 10, 800, 2000, TRUE),
        ('Specific Gravity', 'UR-SG', 'Urine', 'Physical', NULL, 10, 1.005, 1.03, TRUE),
        ('pH (Reaction)', 'UR-PH', 'Urine', 'Physical', NULL, 10, 4.5, 8, TRUE),
        ('Protein (Albumin)', 'UR-PROT', 'Urine', 'Chemical', NULL, 10, NULL, NULL, TRUE),
        ('Glucose (Sugar)', 'UR-GLU', 'Urine', 'Chemical', NULL, 10, NULL, NULL, TRUE),
        ('Ketone Bodies', 'UR-KET', 'Urine', 'Chemical', NULL, 10, NULL, NULL, TRUE),
        ('Bilirubin (Bile pigments)', 'UR-BIL', 'Urine', 'Chemical', NULL, 10, NULL, NULL, TRUE),
        ('Urobilinogen', 'UR-URO', 'Urine', 'Chemical', 'mg/dL', 10, 0.2, 1, TRUE),
        ('Blood / Hemoglobin', 'UR-BLD', 'Urine', 'Chemical', NULL, 10, NULL, NULL, TRUE),
        ('Nitrite', 'UR-NIT', 'Urine', 'Chemical', NULL, 10, NULL, NULL, TRUE),
        ('Leukocyte Esterase', 'UR-LEU', 'Urine', 'Chemical', NULL, 10, NULL, NULL, TRUE),
        ('Red Blood Cells (RBCs)', 'UR-RBC', 'Urine', 'Microscopic', '/HPF', 10, 0, 2, TRUE),
        ('White Blood Cells (Pus cells)', 'UR-WBC', 'Urine', 'Microscopic', '/HPF', 10, 0, 5, TRUE),
        ('Epithelial Cells', 'UR-EP', 'Urine', 'Microscopic', '/HPF', 10, 0, 5, TRUE),
        ('Casts', 'UR-CAST', 'Urine', 'Microscopic', NULL, 10, NULL, NULL, TRUE),
        ('Crystals', 'UR-CRYS', 'Urine', 'Microscopic', NULL, 10, NULL, NULL, TRUE),
        ('Bacteria', 'UR-BACT', 'Urine', 'Microscopic', NULL, 10, NULL, NULL, TRUE),
        ('Yeast / Fungi', 'UR-YEAST', 'Urine', 'Microscopic', NULL, 10, NULL, NULL, TRUE),
        ('Parasites', 'UR-PARA', 'Urine', 'Microscopic', NULL, 10, NULL, NULL, TRUE),
        ('Mucus threads', 'UR-MUC', 'Urine', 'Microscopic', NULL, 10, NULL, NULL, TRUE),
        ('Urine Culture', 'URINE-CS', 'Urine', 'Urinalysis', NULL, 800, NULL, NULL, TRUE),
        ('Urine Pregnancy (UPT)', 'UPT', 'Urine', 'Pregnancy', NULL, 200, NULL, NULL, TRUE),
        ('Blood Culture', 'BLD-CS', 'Microbiology', 'Culture & Sensitivity', NULL, 1200, NULL, NULL, TRUE);

    FOR rec IN
        SELECT r.*, d.id AS department_id, c.id AS category_id
        FROM _seed_test_rows r
        JOIN department d ON lower(d.name) = lower(r.department_name)
        LEFT JOIN test_categories c
            ON lower(c.name) = lower(r.category_name)
           AND c.department_id = d.id
    LOOP
        v_test_id := NULL;

        IF rec.short_code IS NOT NULL THEN
            SELECT t.id INTO v_test_id
            FROM test_definition t
            WHERE lower(t.short_code) = lower(rec.short_code)
            ORDER BY t.id
            LIMIT 1;
        END IF;

        IF v_test_id IS NULL THEN
            SELECT t.id INTO v_test_id
            FROM test_definition t
            WHERE lower(t.test_name) = lower(rec.test_name)
            ORDER BY t.id
            LIMIT 1;
        END IF;

        IF v_test_id IS NULL THEN
            INSERT INTO test_definition (test_name, short_code, department_id, category_id, unit, price, min_range, max_range, active)
            VALUES (rec.test_name, rec.short_code, rec.department_id, rec.category_id, rec.unit, rec.price, rec.min_range, rec.max_range, rec.active);
        ELSE
            UPDATE test_definition
            SET test_name = rec.test_name,
                short_code = rec.short_code,
                department_id = rec.department_id,
                category_id = rec.category_id,
                unit = rec.unit,
                price = rec.price,
                min_range = rec.min_range,
                max_range = rec.max_range,
                active = rec.active
            WHERE id = v_test_id;
        END IF;
    END LOOP;
END $$;

-- 5) Panels and panel->test links
DO $$
DECLARE
    rec RECORD;
    link_rec RECORD;
    v_panel_id INTEGER;
    v_dept_id INTEGER;
    v_test_id BIGINT;
BEGIN
    CREATE TEMP TABLE _seed_panel_rows (
        panel_name TEXT,
        department_name TEXT,
        price NUMERIC,
        active BOOLEAN
    ) ON COMMIT DROP;

    INSERT INTO _seed_panel_rows (panel_name, department_name, price, active) VALUES
        ('CBC / Blood CP', 'Hematology', 650, TRUE),
        ('Urine Routine', 'Urine', 200, TRUE);

    CREATE TEMP TABLE _seed_panel_test_rows (
        panel_name TEXT,
        department_name TEXT,
        test_ref TEXT
    ) ON COMMIT DROP;

    INSERT INTO _seed_panel_test_rows (panel_name, department_name, test_ref) VALUES
        ('CBC / Blood CP', 'Hematology', 'CBC'),
        ('CBC / Blood CP', 'Hematology', 'HB'),
        ('CBC / Blood CP', 'Hematology', 'RBC'),
        ('CBC / Blood CP', 'Hematology', 'HCT'),
        ('CBC / Blood CP', 'Hematology', 'MCV'),
        ('CBC / Blood CP', 'Hematology', 'MCH'),
        ('CBC / Blood CP', 'Hematology', 'MCHC'),
        ('CBC / Blood CP', 'Hematology', 'RDW'),
        ('CBC / Blood CP', 'Hematology', 'WBC'),
        ('CBC / Blood CP', 'Hematology', 'NEUTP'),
        ('CBC / Blood CP', 'Hematology', 'LYMPHP'),
        ('CBC / Blood CP', 'Hematology', 'MONOP'),
        ('CBC / Blood CP', 'Hematology', 'EOSP'),
        ('CBC / Blood CP', 'Hematology', 'BASOP'),
        ('CBC / Blood CP', 'Hematology', 'PLT'),
        ('CBC / Blood CP', 'Hematology', 'MPV'),
        ('CBC / Blood CP', 'Hematology', 'PDW'),
        ('CBC / Blood CP', 'Hematology', 'PCT'),
        ('Urine Routine', 'Urine', 'UR-COLOR'),
        ('Urine Routine', 'Urine', 'UR-APPR'),
        ('Urine Routine', 'Urine', 'UR-ODOR'),
        ('Urine Routine', 'Urine', 'UR-VOL'),
        ('Urine Routine', 'Urine', 'UR-SG'),
        ('Urine Routine', 'Urine', 'UR-PH'),
        ('Urine Routine', 'Urine', 'UR-PROT'),
        ('Urine Routine', 'Urine', 'UR-GLU'),
        ('Urine Routine', 'Urine', 'UR-KET'),
        ('Urine Routine', 'Urine', 'UR-BIL'),
        ('Urine Routine', 'Urine', 'UR-URO'),
        ('Urine Routine', 'Urine', 'UR-BLD'),
        ('Urine Routine', 'Urine', 'UR-NIT'),
        ('Urine Routine', 'Urine', 'UR-LEU'),
        ('Urine Routine', 'Urine', 'UR-RBC'),
        ('Urine Routine', 'Urine', 'UR-WBC'),
        ('Urine Routine', 'Urine', 'UR-EP'),
        ('Urine Routine', 'Urine', 'UR-CAST'),
        ('Urine Routine', 'Urine', 'UR-CRYS'),
        ('Urine Routine', 'Urine', 'UR-BACT'),
        ('Urine Routine', 'Urine', 'UR-YEAST'),
        ('Urine Routine', 'Urine', 'UR-PARA'),
        ('Urine Routine', 'Urine', 'UR-MUC');

    FOR rec IN SELECT * FROM _seed_panel_rows LOOP
        SELECT d.id INTO v_dept_id
        FROM department d
        WHERE lower(d.name) = lower(rec.department_name)
        ORDER BY d.id
        LIMIT 1;

        SELECT p.id INTO v_panel_id
        FROM panel p
        WHERE lower(p.panel_name) = lower(rec.panel_name)
          AND COALESCE(p.department_id, -1) = COALESCE(v_dept_id, -1)
        ORDER BY p.id
        LIMIT 1;

        IF v_panel_id IS NULL THEN
            INSERT INTO panel (panel_name, department_id, price, active)
            VALUES (rec.panel_name, v_dept_id, rec.price, rec.active)
            RETURNING id INTO v_panel_id;
        ELSE
            UPDATE panel
            SET panel_name = rec.panel_name,
                department_id = v_dept_id,
                price = rec.price,
                active = rec.active
            WHERE id = v_panel_id;
        END IF;

        FOR link_rec IN
            SELECT ptr.test_ref
            FROM _seed_panel_test_rows ptr
            WHERE lower(ptr.panel_name) = lower(rec.panel_name)
              AND lower(ptr.department_name) = lower(rec.department_name)
        LOOP
            v_test_id := NULL;
            SELECT t.id INTO v_test_id
            FROM test_definition t
            WHERE (lower(t.short_code) = lower(link_rec.test_ref)
                   OR lower(t.test_name) = lower(link_rec.test_ref))
              AND (v_dept_id IS NULL OR t.department_id = v_dept_id)
            ORDER BY CASE WHEN lower(t.short_code) = lower(link_rec.test_ref) THEN 0 ELSE 1 END, t.id
            LIMIT 1;

            IF v_test_id IS NOT NULL THEN
                INSERT INTO panel_test (panel_id, test_id)
                SELECT v_panel_id, v_test_id
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM panel_test pt
                    WHERE pt.panel_id = v_panel_id
                      AND pt.test_id = v_test_id
                );
            END IF;
        END LOOP;
    END LOOP;
END $$;

-- 6) Reference ranges (insert only for tests that currently have no ranges)
WITH existing_range_tests AS (
    SELECT DISTINCT rr.test_id FROM reference_range rr
),
seed_range(test_short_code, test_name, gender, min_age, max_age, min_val, max_val) AS (
    VALUES
        ('RBC', NULL, 'Female', 18, 120, 4.1, 5.1),
        ('RBC', NULL, 'Male', 18, 120, 4.5, 5.9),
        ('HB', NULL, 'Female', 18, 120, 120, 150),
        ('HB', NULL, 'Male', 18, 120, 130, 170),
        ('HCT', NULL, 'Female', 18, 120, 0.36, 0.48),
        ('HCT', NULL, 'Male', 18, 120, 0.4, 0.52),
        ('MCV', NULL, 'Female', 18, 120, 80, 100),
        ('MCV', NULL, 'Male', 18, 120, 80, 100),
        ('MCH', NULL, 'Female', 18, 120, 27, 33),
        ('MCH', NULL, 'Male', 18, 120, 27, 33),
        ('MCHC', NULL, 'Female', 18, 120, 320, 360),
        ('MCHC', NULL, 'Male', 18, 120, 320, 360),
        ('RDW', NULL, 'Female', 18, 120, 11.5, 14.5),
        ('RDW', NULL, 'Male', 18, 120, 11.5, 14.5),
        ('RDW', NULL, 'Both', 1, 12, 11.5, 15),
        ('WBC', NULL, 'Female', 18, 120, 4, 11),
        ('WBC', NULL, 'Male', 18, 120, 4, 11),
        ('NEUT', NULL, 'Both', 18, 120, 1.81, 7.59),
        ('LYMPH', NULL, 'Both', 18, 120, 1.1, 4.75),
        ('MONO', NULL, 'Both', 18, 120, 0.2, 1),
        ('EOS', NULL, 'Both', 18, 120, 0.02, 0.6),
        ('BASO', NULL, 'Both', 18, 120, 0.01, 0.09),
        ('NEUTP', NULL, 'Both', 18, 120, 40, 70),
        ('NEUTP', NULL, 'Both', 1, 12, 30, 60),
        ('LYMPHP', NULL, 'Both', 18, 120, 20, 40),
        ('LYMPHP', NULL, 'Both', 1, 12, 30, 60),
        ('MONOP', NULL, 'Both', 18, 120, 2, 8),
        ('MONOP', NULL, 'Both', 1, 12, 2, 8),
        ('EOSP', NULL, 'Both', 18, 120, 1, 6),
        ('EOSP', NULL, 'Both', 1, 12, 1, 6),
        ('BASOP', NULL, 'Both', 18, 120, 0, 1),
        ('BASOP', NULL, 'Both', 1, 12, 0, 1),
        ('PLT', NULL, 'Both', 18, 120, 150, 450),
        ('MPV', NULL, 'Both', 1, 120, 7.5, 11.5),
        ('PDW', NULL, 'Both', 1, 120, 9, 17),
        ('PCT', NULL, 'Both', 1, 120, 0.2, 0.4),
        ('ESR', NULL, 'Both', 18, 120, 2, 15),
        ('WBC', NULL, 'Both', 1, 1, 5, 14),
        ('RBC', NULL, 'Both', 1, 1, 4, 5.2),
        ('HB', NULL, 'Both', 1, 1, 110, 140),
        ('HCT', NULL, 'Both', 1, 1, 0.33, 0.43),
        ('MCV', NULL, 'Both', 1, 1, 77, 95),
        ('MCH', NULL, 'Both', 1, 1, 25, 31),
        ('MCHC', NULL, 'Both', 1, 1, 320, 360),
        ('PLT', NULL, 'Both', 1, 1, 150, 450),
        ('NEUT', NULL, 'Both', 1, 1, 1, 7),
        ('LYMPH', NULL, 'Both', 1, 1, 3.5, 11),
        ('MONO', NULL, 'Both', 1, 1, 0.2, 1),
        ('EOS', NULL, 'Both', 1, 1, 0.1, 1),
        ('WBC', NULL, 'Both', 2, 6, 5, 14),
        ('RBC', NULL, 'Both', 2, 6, 4, 5.2),
        ('HB', NULL, 'Both', 2, 6, 110, 140),
        ('HCT', NULL, 'Both', 2, 6, 0.33, 0.43),
        ('MCV', NULL, 'Both', 2, 6, 77, 95),
        ('MCH', NULL, 'Both', 2, 6, 25, 31),
        ('MCHC', NULL, 'Both', 2, 6, 320, 360),
        ('PLT', NULL, 'Both', 2, 6, 150, 450),
        ('NEUT', NULL, 'Both', 2, 6, 1.5, 8),
        ('LYMPH', NULL, 'Both', 2, 6, 6, 9),
        ('MONO', NULL, 'Both', 2, 6, 0.2, 1),
        ('EOS', NULL, 'Both', 2, 6, 0.1, 1),
        ('WBC', NULL, 'Both', 7, 12, 5, 14),
        ('RBC', NULL, 'Both', 7, 12, 4, 5.2),
        ('HB', NULL, 'Both', 7, 12, 110, 140),
        ('HCT', NULL, 'Both', 7, 12, 0.33, 0.43),
        ('MCV', NULL, 'Both', 7, 12, 77, 95),
        ('MCH', NULL, 'Both', 7, 12, 25, 31),
        ('MCHC', NULL, 'Both', 7, 12, 320, 360),
        ('PLT', NULL, 'Both', 7, 12, 150, 450),
        ('NEUT', NULL, 'Both', 7, 12, 2, 8),
        ('LYMPH', NULL, 'Both', 7, 12, 1, 5),
        ('MONO', NULL, 'Both', 7, 12, 0.2, 1),
        ('EOS', NULL, 'Both', 7, 12, 0.1, 1),
        ('GLU-F', NULL, 'Male', 18, 120, 3.1, 6.4),
        ('GLU-F', NULL, 'Female', 18, 120, 3.3, 6.4),
        ('UREA', NULL, 'Male', 18, 120, 2, 9.2),
        ('UREA', NULL, 'Female', 18, 120, 2.2, 7.2),
        ('CREAT', NULL, 'Male', 18, 120, 35, 133),
        ('CREAT', NULL, 'Female', 18, 120, 27, 115),
        ('URIC', NULL, 'Male', 18, 120, 178, 506),
        ('URIC', NULL, 'Female', 18, 120, 119, 434),
        ('CHOL', NULL, 'Both', 18, 120, 3.2, 6.6),
        ('TRIG', NULL, 'Both', 18, 120, 0.6, 2.3),
        ('TBIL', NULL, 'Both', 18, 120, 5, 18),
        ('TP', NULL, 'Both', 18, 120, 57, 83),
        ('ALT', NULL, 'Both', 18, 120, 15, 45),
        ('ALP', NULL, 'Both', 18, 120, 185, 620)
),
resolved AS (
    SELECT sr.*, t.id AS test_id
    FROM seed_range sr
    LEFT JOIN LATERAL (
        SELECT td.id
        FROM test_definition td
        WHERE (sr.test_short_code IS NOT NULL AND lower(td.short_code) = lower(sr.test_short_code))
           OR (sr.test_name IS NOT NULL AND lower(td.test_name) = lower(sr.test_name))
        ORDER BY CASE WHEN sr.test_short_code IS NOT NULL AND lower(td.short_code) = lower(sr.test_short_code) THEN 0 ELSE 1 END, td.id
        LIMIT 1
    ) t ON TRUE
    WHERE sr.min_val IS NOT NULL AND sr.max_val IS NOT NULL
)
INSERT INTO reference_range (test_id, gender, min_age, max_age, min_val, max_val)
SELECT r.test_id, COALESCE(r.gender, 'Both'), r.min_age, r.max_age, r.min_val, r.max_val
FROM resolved r
LEFT JOIN existing_range_tests e ON e.test_id = r.test_id
WHERE r.test_id IS NOT NULL
  AND e.test_id IS NULL;

-- 7) Test recipes / consumption
WITH seed_recipe(test_short_code, test_name, item_name, quantity) AS (
    VALUES
        ('CBC', NULL, 'Purple Top Tube (EDTA)', 1),
        ('CBC', NULL, 'Alcohol Swab', 1),
        ('GLU-F', NULL, 'Glucose Strip', 1),
        ('GLU-F', NULL, 'Alcohol Swab', 1)
),
resolved AS (
    SELECT
        t.id AS test_id,
        i.id AS item_id,
        sr.quantity
    FROM seed_recipe sr
    JOIN inventory_items i ON lower(i.item_name) = lower(sr.item_name)
    LEFT JOIN LATERAL (
        SELECT td.id
        FROM test_definition td
        WHERE (sr.test_short_code IS NOT NULL AND lower(td.short_code) = lower(sr.test_short_code))
           OR (sr.test_name IS NOT NULL AND lower(td.test_name) = lower(sr.test_name))
        ORDER BY CASE WHEN sr.test_short_code IS NOT NULL AND lower(td.short_code) = lower(sr.test_short_code) THEN 0 ELSE 1 END, td.id
        LIMIT 1
    ) t ON TRUE
    WHERE t.id IS NOT NULL
)
UPDATE test_consumption tc
SET quantity = r.quantity
FROM resolved r
WHERE tc.test_id = r.test_id
  AND tc.item_id = r.item_id;

WITH seed_recipe(test_short_code, test_name, item_name, quantity) AS (
    VALUES
        ('CBC', NULL, 'Purple Top Tube (EDTA)', 1),
        ('CBC', NULL, 'Alcohol Swab', 1),
        ('GLU-F', NULL, 'Glucose Strip', 1),
        ('GLU-F', NULL, 'Alcohol Swab', 1)
),
resolved AS (
    SELECT
        t.id AS test_id,
        i.id AS item_id,
        sr.quantity
    FROM seed_recipe sr
    JOIN inventory_items i ON lower(i.item_name) = lower(sr.item_name)
    LEFT JOIN LATERAL (
        SELECT td.id
        FROM test_definition td
        WHERE (sr.test_short_code IS NOT NULL AND lower(td.short_code) = lower(sr.test_short_code))
           OR (sr.test_name IS NOT NULL AND lower(td.test_name) = lower(sr.test_name))
        ORDER BY CASE WHEN sr.test_short_code IS NOT NULL AND lower(td.short_code) = lower(sr.test_short_code) THEN 0 ELSE 1 END, td.id
        LIMIT 1
    ) t ON TRUE
    WHERE t.id IS NOT NULL
)
INSERT INTO test_consumption (test_id, item_id, quantity)
SELECT r.test_id, r.item_id, r.quantity
FROM resolved r
WHERE NOT EXISTS (
    SELECT 1
    FROM test_consumption tc
    WHERE tc.test_id = r.test_id
      AND tc.item_id = r.item_id
);

COMMIT;

-- Verification quick checks
SELECT COUNT(*) AS departments FROM department;
SELECT COUNT(*) AS categories FROM test_categories;
SELECT COUNT(*) AS tests FROM test_definition;
SELECT COUNT(*) AS panels FROM panel;
SELECT COUNT(*) AS panel_links FROM panel_test;
SELECT COUNT(*) AS ranges FROM reference_range;
SELECT COUNT(*) AS inventory_items FROM inventory_items;
SELECT COUNT(*) AS test_recipes FROM test_consumption;
