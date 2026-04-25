BEGIN;

DO $$
DECLARE
    v_dept_id INTEGER;
    v_category_id BIGINT;
    v_panel_id INTEGER;
    rec RECORD;
    v_test_id BIGINT;
BEGIN
    INSERT INTO department (name, code, active)
    VALUES ('Semen Analysis', 'SEM', TRUE)
    ON CONFLICT (name) DO UPDATE
    SET code = EXCLUDED.code,
        active = EXCLUDED.active;

    SELECT d.id INTO v_dept_id
    FROM department d
    WHERE lower(d.name) = lower('Semen Analysis')
    ORDER BY d.id
    LIMIT 1;

    IF v_dept_id IS NULL THEN
        RAISE EXCEPTION 'Department Semen Analysis not found';
    END IF;

    SELECT tc.id INTO v_category_id
    FROM test_categories tc
    WHERE lower(tc.name) = lower('Semen Analysis')
      AND tc.department_id = v_dept_id
    ORDER BY tc.id
    LIMIT 1;

    IF v_category_id IS NULL THEN
        INSERT INTO test_categories (name, department_id, description, is_active)
        VALUES ('Semen Analysis', v_dept_id, 'Routine semen analysis profile', TRUE)
        RETURNING id INTO v_category_id;
    ELSE
        UPDATE test_categories
        SET description = 'Routine semen analysis profile',
            is_active = TRUE
        WHERE id = v_category_id;
    END IF;

    CREATE TEMP TABLE _semen_tests (
        test_name TEXT,
        short_code TEXT,
        unit TEXT,
        price NUMERIC,
        min_range NUMERIC,
        max_range NUMERIC
    ) ON COMMIT DROP;

    INSERT INTO _semen_tests (test_name, short_code, unit, price, min_range, max_range) VALUES
        ('Sample Collection', 'SEM-COLL', NULL, 50, NULL, NULL),
        ('Duration of Abstinence', 'SEM-ABS', 'days', 50, NULL, NULL),
        ('Time of Sample Production', 'SEM-TSP', 'HH:mm', 50, NULL, NULL),
        ('Analysis Time', 'SEM-AT', 'HH:mm', 50, NULL, NULL),
        ('Colour', 'SEM-COL', NULL, 75, NULL, NULL),
        ('Volume', 'SEM-VOL', 'mL', 75, 1.5, 5),
        ('Liquefaction Time', 'SEM-LIQ', 'min', 75, 20, 20),
        ('pH', 'SEM-PH', NULL, 75, 7.5, 8.5),
        ('Rapid Progression', 'SEM-RAP', '%', 100, NULL, NULL),
        ('Slow Progression', 'SEM-SLO', '%', 100, NULL, NULL),
        ('Immotile', 'SEM-IMM', '%', 100, NULL, NULL),
        ('Count', 'SEM-COUNT', 'Millions/mL', 120, 20, 120),
        ('Pus cells', 'SEM-PUS', '/HPF', 120, 0, 5);

    FOR rec IN SELECT * FROM _semen_tests LOOP
        v_test_id := NULL;

        SELECT td.id INTO v_test_id
        FROM test_definition td
        WHERE lower(td.short_code) = lower(rec.short_code)
        ORDER BY td.id
        LIMIT 1;

        IF v_test_id IS NULL THEN
            SELECT td.id INTO v_test_id
            FROM test_definition td
            WHERE lower(td.test_name) = lower(rec.test_name)
              AND td.department_id = v_dept_id
            ORDER BY td.id
            LIMIT 1;
        END IF;

        IF v_test_id IS NULL THEN
            INSERT INTO test_definition (
                test_name, short_code, department_id, category_id, unit, price, min_range, max_range, active
            ) VALUES (
                rec.test_name, rec.short_code, v_dept_id, v_category_id, rec.unit, rec.price, rec.min_range, rec.max_range, TRUE
            )
            RETURNING id INTO v_test_id;
        ELSE
            UPDATE test_definition
            SET test_name = rec.test_name,
                short_code = rec.short_code,
                department_id = v_dept_id,
                category_id = v_category_id,
                unit = rec.unit,
                price = rec.price,
                min_range = rec.min_range,
                max_range = rec.max_range,
                active = TRUE
            WHERE id = v_test_id;
        END IF;
    END LOOP;

    SELECT p.id INTO v_panel_id
    FROM panel p
    WHERE lower(p.panel_name) = lower('Semen Analysis')
      AND p.department_id = v_dept_id
    ORDER BY p.id
    LIMIT 1;

    IF v_panel_id IS NULL THEN
        INSERT INTO panel (panel_name, department_id, active)
        VALUES ('Semen Analysis', v_dept_id, TRUE)
        RETURNING id INTO v_panel_id;
    ELSE
        UPDATE panel
        SET panel_name = 'Semen Analysis',
            department_id = v_dept_id,
            active = TRUE
        WHERE id = v_panel_id;
    END IF;

    DELETE FROM panel_test
    WHERE panel_id = v_panel_id;

    INSERT INTO panel_test (panel_id, test_id)
    SELECT v_panel_id, td.id
    FROM _semen_tests st
    JOIN test_definition td ON lower(td.short_code) = lower(st.short_code);

    DELETE FROM reference_range rr
    USING test_definition td
    WHERE rr.test_id = td.id
      AND td.short_code IN ('SEM-COL', 'SEM-VOL', 'SEM-LIQ', 'SEM-PH', 'SEM-COUNT', 'SEM-PUS');

    INSERT INTO reference_range (test_id, gender, min_age, max_age, min_val, max_val, reference_text)
    SELECT td.id, 'Male', 18, 120, sr.min_val, sr.max_val, sr.reference_text
    FROM (
        VALUES
            ('SEM-COL', NULL::numeric, NULL::numeric, 'Pale white - Creamy white'),
            ('SEM-VOL', 1.5::numeric, 5::numeric, NULL),
            ('SEM-LIQ', 20::numeric, 20::numeric, NULL),
            ('SEM-PH', 7.5::numeric, 8.5::numeric, NULL),
            ('SEM-COUNT', 20::numeric, 120::numeric, NULL),
            ('SEM-PUS', 0::numeric, 5::numeric, NULL)
    ) AS sr(short_code, min_val, max_val, reference_text)
    JOIN test_definition td ON lower(td.short_code) = lower(sr.short_code);
END $$;

COMMIT;
