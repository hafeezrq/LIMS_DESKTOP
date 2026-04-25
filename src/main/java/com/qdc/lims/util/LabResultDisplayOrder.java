package com.qdc.lims.util;

import com.qdc.lims.entity.LabResult;
import com.qdc.lims.entity.TestDefinition;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

/**
 * Shared display ordering rules for lab results in UI and printed reports.
 * Applies known clinical panel orders first (CBC/CBS, Urine Analysis), then
 * falls back to test name.
 */
public final class LabResultDisplayOrder {

    private static final Map<String, Integer> CODE_ORDER = Map.ofEntries(
            Map.entry("WBC", 1),
            Map.entry("RBC", 2),
            Map.entry("HGB", 3),
            Map.entry("HB", 3),
            Map.entry("HCT", 4),
            Map.entry("PCV", 4),
            Map.entry("MCV", 5),
            Map.entry("MCH", 6),
            Map.entry("MCHC", 7),
            Map.entry("PLT", 8),

            Map.entry("GR", 9),
            Map.entry("NEUT", 9),
            Map.entry("NEUTP", 9),
            Map.entry("LY", 10),
            Map.entry("LYMPH", 10),
            Map.entry("LYMPHP", 10),
            Map.entry("MO", 11),
            Map.entry("MONO", 11),
            Map.entry("MONOP", 11),

            Map.entry("RDW-CV", 12),
            Map.entry("RDW", 12),
            Map.entry("RDW-SD", 13),
            Map.entry("PCT", 14),
            Map.entry("MPV", 15),
            Map.entry("PDW", 16),

            Map.entry("UR-COLOR", 101),
            Map.entry("COLOR", 101),
            Map.entry("COLOUR", 101),
            Map.entry("UR-APPR", 102),
            Map.entry("TURBIDITY", 102),
            Map.entry("TURBID", 102),
            Map.entry("UR-SED", 103),
            Map.entry("SEDIMENT", 103),
            Map.entry("UR-PH", 104),
            Map.entry("PH", 104),
            Map.entry("UR-SG", 105),
            Map.entry("SG", 105),
            Map.entry("SPG", 105),
            Map.entry("UR-GLU", 106),
            Map.entry("GLUCOSE", 106),
            Map.entry("UR-PROT", 107),
            Map.entry("ALBUMIN", 107),
            Map.entry("PROTEIN", 107),
            Map.entry("UR-KET", 108),
            Map.entry("KETONES", 108),
            Map.entry("UR-NIT", 109),
            Map.entry("NITRITE", 109),
            Map.entry("UR-BIL", 110),
            Map.entry("BILIRUBIN", 110),
            Map.entry("BILE-PIGMENTS", 111),
            Map.entry("BILE-PIGMENT", 111),
            Map.entry("BILEPIGMENTS", 111),
            Map.entry("BILE-SALTS", 112),
            Map.entry("BILE-SALT", 112),
            Map.entry("BILESALTS", 112),
            Map.entry("UR-BLD", 113),
            Map.entry("BLOOD", 113),
            Map.entry("UR-WBC", 114),
            Map.entry("PUS", 114),
            Map.entry("PUS-CELLS", 114),
            Map.entry("UR-RBC", 115),
            Map.entry("RBCS", 115),
            Map.entry("UR-EP", 116),
            Map.entry("EPITHELIAL", 116),

            Map.entry("SEM-COLL", 201),
            Map.entry("SEM-ABS", 202),
            Map.entry("SEM-TSP", 203),
            Map.entry("SEM-AT", 204),
            Map.entry("SEM-COL", 205),
            Map.entry("SEM-VOL", 206),
            Map.entry("SEM-LIQ", 207),
            Map.entry("SEM-PH", 208),
            Map.entry("SEM-RAP", 209),
            Map.entry("SEM-SLO", 210),
            Map.entry("SEM-IMM", 211),
            Map.entry("SEM-COUNT", 212),
            Map.entry("SEM-PUS", 213));

    private LabResultDisplayOrder() {
    }

    public static Comparator<LabResult> comparator() {
        return Comparator
                .comparingInt((LabResult result) -> codeRank(result != null ? result.getTestDefinition() : null))
                .thenComparing(result -> safeName(result != null ? result.getTestDefinition() : null),
                        String.CASE_INSENSITIVE_ORDER);
    }

    private static int codeRank(TestDefinition testDefinition) {
        if (testDefinition == null) {
            return Integer.MAX_VALUE;
        }
        String code = normalize(testDefinition.getShortCode());
        if (!code.isEmpty()) {
            int codeOrder = CODE_ORDER.getOrDefault(code, Integer.MAX_VALUE);
            if (codeOrder != Integer.MAX_VALUE) {
                return codeOrder;
            }
        }
        return nameRank(normalize(testDefinition.getTestName()));
    }

    private static String safeName(TestDefinition testDefinition) {
        if (testDefinition == null || testDefinition.getTestName() == null) {
            return "";
        }
        return testDefinition.getTestName();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static int nameRank(String normalizedName) {
        if (normalizedName.isEmpty()) {
            return Integer.MAX_VALUE;
        }

        if (normalizedName.contains("COLOUR") || normalizedName.contains("COLOR")) {
            return 101;
        }
        if (normalizedName.contains("TURBIDITY")
                || normalizedName.contains("TURBID")
                || normalizedName.contains("APPEARANCE")
                || normalizedName.contains("CLARITY")) {
            return 102;
        }
        if (normalizedName.contains("SEDIMENT")) {
            return 103;
        }
        if ("PH".equals(normalizedName) || normalizedName.contains("PH (")) {
            return 104;
        }
        if (normalizedName.contains("SP. GRAVITY")
                || normalizedName.contains("SP GRAVITY")
                || normalizedName.contains("SPECIFIC GRAVITY")) {
            return 105;
        }
        if (normalizedName.contains("GLUCOSE")) {
            return 106;
        }
        if (normalizedName.contains("ALBUMIN") || normalizedName.contains("PROTEIN")) {
            return 107;
        }
        if (normalizedName.contains("KETONE")) {
            return 108;
        }
        if (normalizedName.contains("NITRITE")) {
            return 109;
        }
        if (normalizedName.contains("BILIRUBIN")) {
            return 110;
        }
        if (normalizedName.contains("BILE PIGMENT")) {
            return 111;
        }
        if (normalizedName.contains("BILE SALT")) {
            return 112;
        }
        if (normalizedName.contains("BLOOD")) {
            return 113;
        }
        if (normalizedName.contains("PUS CELL") || normalizedName.contains("WHITE BLOOD CELL")) {
            return 114;
        }
        if (normalizedName.contains("RED BLOOD CELL")) {
            return 115;
        }
        if (normalizedName.contains("EPITHELIAL CELL")) {
            return 116;
        }
        return Integer.MAX_VALUE;
    }
}
