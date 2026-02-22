# LIMS First-Run UAT (Click-by-Click)

Use this checklist on a fresh install to verify manual setup and Create Order behavior.

## 1. Fresh Start

1. Launch the app.
2. Log in as Admin.
3. Open `Admin Dashboard -> Test Configuration -> Departments`.
4. Click `Save` with empty department name.
5. Expected: validation warning, no crash.

## 2. Create Departments

1. In Departments, add:
   - `Hematology` (code: `HEM`)
   - `Urine` (code: `URI`)
2. Confirm both rows appear in the table.

## 3. Create Test Definitions

1. Open `Admin Dashboard -> Test Configuration -> Test Definitions`.
2. Click `New Test` and then cancel.
3. Expected: dialog opens and closes safely.
4. Click `New Test` and create:
   - Name: `Complete Blood Count (CBC)`
   - Short Code: `CBC`
   - Department: `Hematology`
   - Category: `CBC / Hemogram`
   - Price: `650`
5. Add more Hematology tests, for example:
   - `HB`, `RBC`, `WBC`, `PLT` (same department/category)
6. Add Urine tests, for example:
   - `UR-COLOR`, `UR-PH` (department `Urine`)
7. Enter invalid price text for one test and click save.
8. Expected: warning shown, no crash.

## 4. Create Panels (No SQL Required)

1. Open `Admin Dashboard -> Test Configuration -> Panels`.
2. Click `New Panel`.
3. Create Hematology panel:
   - Panel Name: `CBC / Blood CP`
   - Department: `Hematology`
   - Panel Price: `650`
   - Active: checked
   - Select tests: `CBC`, `HB`, `RBC`, `WBC`, `PLT`
4. Click `Save Panel`.
5. Expected: panel appears in table.
6. Try creating the same panel name in the same department again.
7. Expected: duplicate warning.
8. Create Urine panel:
   - Panel Name: `Urine Routine`
   - Department: `Urine`
   - Panel Price: `200`
   - Active: checked
   - Select urine tests
9. Click `Save Panel`.
10. Click `Close`.
11. Expected: current admin tab closes safely.

## 5. Verify Create Order

1. Switch to Reception dashboard.
2. Open `Create Lab Order`.
3. Expected:
   - `Hematology` tab appears.
   - `CBC / Blood CP` panel tab appears.
   - `Bill as panel` checkbox is visible.
   - `Urine` and `Urine Routine` appear.
4. In Hematology panel, check `Bill as panel`.
5. Expected: panel member tests auto-select.
6. Uncheck `Bill as panel`.
7. Expected: panel member tests auto-deselect.
8. Confirm CBC/Hemogram tests linked to panel do not appear duplicated in `Other Tests`.

## 6. Quick Active/Inactive Check

1. Go back to `Admin -> Test Configuration -> Panels`.
2. Edit `CBC / Blood CP`, uncheck `Active`, save.
3. Re-open `Create Lab Order`.
4. Expected: `CBC / Blood CP` panel is hidden.
5. Re-enable `Active`, save.
6. Expected: panel appears again in Create Order.

## 7. Pass Criteria

1. No null/empty-data crash during setup.
2. Departments -> Tests -> Panels can be configured fully from UI.
3. Create Order reflects panel/test data correctly.

## 8. If a Step Fails

Capture:
1. Step number
2. Screenshot
3. Exact error text

Then send those three items for a targeted fix.
