# Clinical Test Data Entry Guide (Admin Training)

This guide explains the correct order and method to configure clinical test master data in the app so Create Order works correctly.

Use this for:
- fresh setup on a new installation
- staff training
- troubleshooting why tests/panels do not appear correctly

## 1. Who Should Do This

- Login as an Admin user.
- Open `Admin Dashboard`.
- Use menu: `Test Configuration`.

## 2. Correct Setup Order

Always follow this order:

1. `Departments`
2. `Test Definitions`
3. `Panels`
4. `Reference Ranges` (optional but recommended)
5. `Test Recipes` (optional, for inventory deduction)
6. Validate in `Reception -> Create Lab Order`

If you skip order, UI behavior can be incomplete (for example, panels not showing because tests/departments are missing).

## 3. Step-by-Step Data Entry

### Step A: Create Departments

Path:
- `Admin Dashboard -> Test Configuration -> Departments`

What to enter:
- `Name` (required, unique)
- `Code` (optional but recommended)

Example:
- Name: `Hematology`, Code: `HEM`
- Name: `Urine`, Code: `URI`

Important:
- You must create at least one department before adding tests.

### Step B: Create Test Definitions

Path:
- `Admin Dashboard -> Test Configuration -> Test Definitions -> New Test`

Fields:
- `Test Name` (required)
- `Short Code` (recommended)
- `Department` (required)
- `Category` (optional, but recommended)
- `Unit` (optional)
- `Price` (optional)

Category behavior:
- Category box is editable.
- If category text does not exist in selected department, the system creates it when saving the test.

Example entries:

Hematology:
- Test Name: `Complete Blood Count (CBC)`, Short Code: `CBC`, Department: `Hematology`, Category: `CBC / Hemogram`, Price: `650`
- `Hemoglobin` (`HB`)
- `Red Blood Cell Count` (`RBC`)
- `White Blood Cell Count` (`WBC`)
- `Platelet Count` (`PLT`)

Urine:
- `Color` (`UR-COLOR`), Department: `Urine`, Category: `Physical`
- `pH (Reaction)` (`UR-PH`), Department: `Urine`, Category: `Physical`
- `Protein (Albumin)` (`UR-PROT`), Department: `Urine`, Category: `Chemical`

Validation notes:
- Missing Test Name or Department will block save.
- Invalid Price format will block save.

### Step C: Create Panels

Path:
- `Admin Dashboard -> Test Configuration -> Panels`

Fields:
- `Panel Name` (required)
- `Department` (required)
- `Panel Price` (required, must be > 0)
- `Active` (checked means visible in Create Order)
- Select at least one test from list

Example 1 (Hematology panel):
- Panel Name: `CBC / Blood CP`
- Department: `Hematology`
- Panel Price: `650`
- Active: checked
- Select tests: `CBC`, `HB`, `RBC`, `WBC`, `PLT` (and other CBC components if used)

Example 2 (Urine panel):
- Panel Name: `Urine Routine`
- Department: `Urine`
- Panel Price: `200`
- Active: checked
- Select common urine tests

Validation notes:
- Panel name must be unique within same department.
- Duplicate same name + same department is blocked.
- No tests selected is blocked.

## 4. Validate in Create Order (Reception)

Path:
- `Reception Dashboard -> Patient -> Create Lab Order`

What you should see:
- Department tabs (for departments that have tests)
- Under department, panel tabs (for active panels)
- `Bill as panel` checkbox inside each panel tab

Expected behavior:
- When `Bill as panel` is checked:
  - panel tests auto-select
- When `Bill as panel` is unchecked:
  - panel tests auto-deselect (unless those tests are selected by another selected panel)
- Tests included in a panel should not be duplicated in `Other Tests` for that same department.

## 5. Full Worked Example (Recommended Training Exercise)

Use this exercise during training to verify end-to-end setup:

1. Create departments:
- `Hematology (HEM)`
- `Urine (URI)`

2. Add tests:
- Add 5+ hematology tests under category `CBC / Hemogram`
- Add 5+ urine tests under categories `Physical`, `Chemical`, or `Microscopic`

3. Create panels:
- `CBC / Blood CP` under `Hematology`
- `Urine Routine` under `Urine`

4. Open Create Order:
- Confirm both departments appear
- Confirm both panel tabs appear
- Check/uncheck `Bill as panel` and confirm selection behavior
- Confirm no duplicate CBC tests in Hematology `Other Tests`

5. Inactivate panel test:
- Set panel `Active` unchecked in Panel Management
- Re-open Create Order and confirm panel is hidden
- Re-enable and confirm it returns

## 6. Common Mistakes and Fixes

### Issue: Panel not showing in Create Order

Check:
- Panel `Active` is checked.
- Panel has valid department.
- Panel has mapped tests.
- Mapped tests are active.

### Issue: Department tab not showing

Check:
- At least one active test exists under that department.

### Issue: Panel exists but tests look wrong

Check:
- Tests are mapped to correct department.
- Panel uses tests from same intended department.
- Short codes are unique and correct.

### Issue: Validation errors while saving

Typical causes:
- Missing required field
- Price not numeric
- Duplicate panel name in same department

## 7. Data Entry Standards (Recommended)

- Department names: consistent, singular style (for example `Hematology`, `Urine`).
- Category names: consistent by department (for example `CBC / Hemogram`).
- Short codes: unique and stable (`CBC`, `HB`, `UR-PH`).
- Avoid creating multiple near-duplicate names for same concept.
- Keep panel names user-friendly for reception staff.

## 8. Optional Advanced Setup

### Reference Ranges

Path:
- `Admin Dashboard -> Test Configuration -> Reference Ranges`

Use to define:
- Gender (`Both/Male/Female`)
- Age min/max
- Normal min/max values

### Test Recipes (Inventory Consumption)

Path:
- `Admin Dashboard -> Test Configuration -> Test Recipes`

Use to define:
- which inventory items are consumed by each test
- quantity consumed per test

## 9. Recommended Go-Live Checklist

Before go-live, verify:

1. All departments are created and clean.
2. All frequently used tests are added with correct department/category.
3. All required panels are created and active.
4. Create Order tabs/selection behavior is validated.
5. Optional ranges/recipes are entered for key tests.
6. One complete test order is created successfully in Reception.

