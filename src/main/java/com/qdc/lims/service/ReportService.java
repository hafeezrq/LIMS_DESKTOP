package com.qdc.lims.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.qdc.lims.entity.*;
import com.qdc.lims.repository.LabOrderRepository;
import com.qdc.lims.repository.ReferenceRangeRepository;
import com.qdc.lims.util.LabResultDisplayOrder;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates printable PDF lab reports for completed orders.
 */
@Service
public class ReportService {

    private final LabOrderRepository orderRepo;
    private final BrandingService brandingService;
    private final LocaleFormatService localeFormatService;
    private final ReferenceRangeRepository referenceRangeRepository;

    /**
     * Creates the report service.
     *
     * @param orderRepo       lab order repository
     * @param brandingService branding and lab profile service
     */
    public ReportService(LabOrderRepository orderRepo,
            BrandingService brandingService,
            LocaleFormatService localeFormatService,
            ReferenceRangeRepository referenceRangeRepository) {
        this.orderRepo = orderRepo;
        this.brandingService = brandingService;
        this.localeFormatService = localeFormatService;
        this.referenceRangeRepository = referenceRangeRepository;
    }

    /**
     * Builds a PDF report for the given order id.
     *
     * @param orderId lab order id
     * @return PDF document bytes
     */
    public byte[] generatePdfReport(Long orderId) {
        LabOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        Patient patient = order.getPatient();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);

            document.open();

            // 1. Header
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Color.BLUE);
            Paragraph title = new Paragraph(brandingService.getReportHeaderText(), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            addLabContactDetails(document);
            document.add(new Paragraph("\n"));

            // 2. Patient Details
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
            document.add(new Paragraph("Patient Name: " + patient.getFullName(), normalFont));
            document.add(new Paragraph("MRN: " + patient.getMrn(), normalFont));
            document.add(new Paragraph("Age/Gender: "
                    + localeFormatService.formatAge(patient.getAge(), patient.getAgeUnit())
                    + " / "
                    + (patient.getGender() != null ? patient.getGender() : "-"), normalFont));
            document.add(new Paragraph("Date: " + localeFormatService.formatDate(order.getOrderDate().toLocalDate()),
                    normalFont));
            document.add(new Paragraph("\n"));

            // 3. Results grouped by department/category
            Map<String, List<LabResult>> resultsByDepartment = order.getResults().stream()
                    .filter(result -> result.getResultValue() != null && !result.getResultValue().trim().isEmpty())
                    .collect(Collectors.groupingBy(
                            result -> result.getTestDefinition() != null
                                    && result.getTestDefinition().getDepartment() != null
                                            ? result.getTestDefinition().getDepartment().getName()
                                            : "Other",
                            LinkedHashMap::new,
                            Collectors.toList()));

            List<String> sortedDepartments = resultsByDepartment.keySet().stream()
                    .sorted(Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .toList();

            for (String department : sortedDepartments) {
                List<LabResult> results = resultsByDepartment.get(department);
                if (results == null || results.isEmpty()) {
                    continue;
                }
                addSectionHeader(document, department);
                addResultsTable(document, results, patient);
                document.add(new Paragraph(" "));
            }

            // 4. Footer (optional)
            String footerText = brandingService.getReportFooterText();
            if (!footerText.isBlank()) {
                document.add(new Paragraph("\n\n"));
                Paragraph footer = new Paragraph(footerText,
                        FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10));
                footer.setAlignment(Element.ALIGN_CENTER);
                document.add(footer);
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error generating PDF", e);
        }
    }

    /**
     * Adds a formatted cell to the report table.
     *
     * @param table    target table
     * @param text     cell text (null-safe)
     * @param isHeader whether the cell is a header cell
     */
    private void addCell(PdfPTable table, String text, boolean isHeader) {
        String safeText = (text != null) ? text : "";

        Font font = isHeader ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE)
                : FontFactory.getFont(FontFactory.HELVETICA, 12);

        PdfPCell cell = new PdfPCell(new Phrase(safeText, font));
        cell.setPadding(5);
        if (isHeader) {
            cell.setBackgroundColor(Color.DARK_GRAY);
        }
        table.addCell(cell);
    }

    private void addSectionHeader(Document document, String title) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.DARK_GRAY);
        Chunk chunk = new Chunk(title, sectionFont);
        chunk.setUnderline(0.8f, -2f);
        Paragraph section = new Paragraph(chunk);
        section.setSpacingBefore(8);
        section.setSpacingAfter(4);
        document.add(section);
    }

    private void addResultsTable(Document document, List<LabResult> results, Patient patient) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{4, 2, 2, 3});

        addCell(table, "Test Name", true);
        addCell(table, "Result", true);
        addCell(table, "Unit", true);
        addCell(table, "Reference Range", true);

        List<LabResult> sortedResults = results.stream()
                .sorted(LabResultDisplayOrder.comparator())
                .toList();

        for (LabResult result : sortedResults) {
            addCell(table, result.getTestDefinition().getTestName(), false);

            Font resultFont = FontFactory.getFont(FontFactory.HELVETICA, 11);
            if (result.isAbnormal()) {
                resultFont.setColor(Color.RED);
                resultFont.setStyle(Font.BOLD);
            }
            PdfPCell valueCell = new PdfPCell(new Phrase(result.getResultValue(), resultFont));
            valueCell.setPadding(5);
            table.addCell(valueCell);

            String unit = result.getTestDefinition().getUnit();
            addCell(table, unit != null ? unit : "", false);

            String range = formatRange(result.getTestDefinition(), patient);
            addCell(table, range, false);
        }

        document.add(table);
    }

    private String formatRange(TestDefinition testDefinition, Patient patient) {
        BigDecimal min = testDefinition.getMinRange();
        BigDecimal max = testDefinition.getMaxRange();
        ReferenceRange range = findMatchingRange(testDefinition, patient);
        if (range != null) {
            if (range.getReferenceText() != null && !range.getReferenceText().trim().isEmpty()) {
                return range.getReferenceText().trim();
            }
            min = range.getMinVal();
            max = range.getMaxVal();
        }
        if (min != null && max != null) {
            return min + " - " + max;
        }
        return "";
    }

    private ReferenceRange findMatchingRange(TestDefinition testDefinition, Patient patient) {
        if (testDefinition == null || testDefinition.getId() == null) {
            return null;
        }
        List<ReferenceRange> ranges = referenceRangeRepository.findByTestId(testDefinition.getId());
        if (ranges == null || ranges.isEmpty()) {
            return null;
        }
        Integer age = toAgeInYears(patient);
        String gender = patient != null ? patient.getGender() : null;

        return ranges.stream()
                .filter(range -> matchesRange(range, age, gender))
                .sorted((a, b) -> {
                    int genderScoreA = genderScore(a, gender);
                    int genderScoreB = genderScore(b, gender);
                    if (genderScoreA != genderScoreB) {
                        return Integer.compare(genderScoreB, genderScoreA);
                    }
                    Integer minA = a.getMinAge();
                    Integer minB = b.getMinAge();
                    if (minA == null && minB == null) {
                        return 0;
                    }
                    if (minA == null) {
                        return 1;
                    }
                    if (minB == null) {
                        return -1;
                    }
                    return Integer.compare(minA, minB);
                })
                .findFirst()
                .orElse(null);
    }

    private boolean matchesRange(ReferenceRange range, Integer age, String gender) {
        if (range == null) {
            return false;
        }
        String rangeGender = range.getGender();
        if (gender != null && rangeGender != null && !"Both".equalsIgnoreCase(rangeGender)
                && !rangeGender.equalsIgnoreCase(gender)) {
            return false;
        }
        if (age != null) {
            if (range.getMinAge() != null && age < range.getMinAge()) {
                return false;
            }
            if (range.getMaxAge() != null && age > range.getMaxAge()) {
                return false;
            }
        }
        return true;
    }

    private int genderScore(ReferenceRange range, String gender) {
        if (range == null) {
            return 0;
        }
        String rangeGender = range.getGender();
        if (gender != null && rangeGender != null && rangeGender.equalsIgnoreCase(gender)) {
            return 2;
        }
        if (rangeGender != null && "Both".equalsIgnoreCase(rangeGender)) {
            return 1;
        }
        return 0;
    }

    private Integer toAgeInYears(Patient patient) {
        if (patient == null || patient.getAge() == null) {
            return null;
        }
        int age = patient.getAge();
        String ageUnit = patient.getAgeUnit();
        if (ageUnit != null && ageUnit.equalsIgnoreCase("Months")) {
            return Math.max(0, age / 12);
        }
        if (ageUnit != null && ageUnit.equalsIgnoreCase("Days")) {
            return 0;
        }
        return age;
    }

    private void addLabContactDetails(Document document) throws DocumentException {
        Font contactFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);

        String address = brandingService.getClinicAddress();
        if (!address.isBlank()) {
            Paragraph addressPara = new Paragraph(address, contactFont);
            addressPara.setAlignment(Element.ALIGN_CENTER);
            document.add(addressPara);
        }

        String phone = brandingService.getClinicPhone();
        String email = brandingService.getClinicEmail();
        StringBuilder contactLine = new StringBuilder();
        if (!phone.isBlank()) {
            contactLine.append(phone);
        }
        if (!email.isBlank()) {
            if (!contactLine.isEmpty()) {
                contactLine.append(" | ");
            }
            contactLine.append(email);
        }

        if (!contactLine.isEmpty()) {
            Paragraph contactPara = new Paragraph(contactLine.toString(), contactFont);
            contactPara.setAlignment(Element.ALIGN_CENTER);
            document.add(contactPara);
        }
    }
}
