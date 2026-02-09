package com.qdc.lims.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Currency;
import java.util.Locale;
import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;

/**
 * Locale-aware formatting helper for dates, times, and currency.
 */
@Service
public class LocaleFormatService {

    private static final Locale PAKISTAN_LOCALE = Locale.forLanguageTag("en-PK");

    private final ConfigService configService;
    private final Locale locale;
    private final DateTimeFormatter dateFormatter;
    private final DateTimeFormatter timeFormatter;
    private final DateTimeFormatter dateTimeFormatter;

    public LocaleFormatService(ConfigService configService) {
        this.configService = configService;
        this.locale = PAKISTAN_LOCALE;
        this.dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
        this.timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale);
        this.dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale);
    }

    public String formatCurrency(BigDecimal amount) {
        NumberFormat format = createCurrencyFormat();
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }

    public String formatCurrencyNullable(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        return formatCurrency(amount);
    }

    public String formatNumber(BigDecimal amount) {
        NumberFormat format = createNumberFormat();
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }

    public BigDecimal parseNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        String trimmed = raw.trim();
        NumberFormat format = createNumberFormat();
        try {
            Number parsed = format.parse(trimmed);
            return parsed != null ? new BigDecimal(parsed.toString()) : BigDecimal.ZERO;
        } catch (ParseException ignored) {
        }

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
        char grouping = symbols.getGroupingSeparator();
        char decimal = symbols.getDecimalSeparator();
        String normalized = trimmed.replace(String.valueOf(grouping), "");
        if (decimal != '.') {
            normalized = normalized.replace(decimal, '.');
        }
        normalized = normalized.replaceAll("[^0-9.+-]", "");
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    public String formatDate(LocalDate date) {
        if (date == null) {
            return "-";
        }
        return date.format(dateFormatter);
    }

    public String formatTime(LocalTime time) {
        if (time == null) {
            return "-";
        }
        return time.format(timeFormatter);
    }

    public String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "-";
        }
        return dateTime.format(dateTimeFormatter);
    }

    public StringConverter<LocalDate> createDateConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                if (date == null) {
                    return "";
                }
                return date.format(dateFormatter);
            }

            @Override
            public LocalDate fromString(String string) {
                if (string == null || string.isBlank()) {
                    return null;
                }
                return LocalDate.parse(string, dateFormatter);
            }
        };
    }

    public void applyDatePickerLocale(DatePicker... datePickers) {
        if (datePickers == null || datePickers.length == 0) {
            return;
        }
        StringConverter<LocalDate> converter = createDateConverter();
        for (DatePicker datePicker : datePickers) {
            if (datePicker != null) {
                datePicker.setConverter(converter);
            }
        }
    }

    private NumberFormat createCurrencyFormat() {
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        String overrideSymbol = resolveCurrencySymbol();
        if (overrideSymbol != null && !overrideSymbol.isBlank() && format instanceof DecimalFormat decimalFormat) {
            DecimalFormatSymbols symbols = decimalFormat.getDecimalFormatSymbols();
            symbols.setCurrencySymbol(overrideSymbol);
            decimalFormat.setDecimalFormatSymbols(symbols);
        }
        return format;
    }

    private NumberFormat createNumberFormat() {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format;
    }

    private String resolveCurrencySymbol() {
        String configured = configService.getTrimmed("CURRENCY_SYMBOL", "");
        String localeSymbol = getLocaleCurrencySymbol();
        String defaultSymbol = normalizePakistanCurrencySymbol(localeSymbol);

        if (configured.isBlank() || configured.equalsIgnoreCase("AUTO") || configured.equalsIgnoreCase("DEFAULT")) {
            return defaultSymbol;
        }

        if (!localeSymbol.equals(configured)) {
            if ("$".equals(configured) && !"$".equals(localeSymbol)) {
                return defaultSymbol;
            }
        }

        if (isPakistanLocale() && "Rs".equalsIgnoreCase(configured)) {
            return "Rs.";
        }

        return configured;
    }

    private String normalizePakistanCurrencySymbol(String symbol) {
        if (!isPakistanLocale()) {
            return symbol;
        }
        if (symbol == null || symbol.isBlank() || "Rs".equalsIgnoreCase(symbol) || "PKR".equalsIgnoreCase(symbol)) {
            return "Rs.";
        }
        return symbol;
    }

    private boolean isPakistanLocale() {
        return "PK".equalsIgnoreCase(locale.getCountry());
    }

    private String getLocaleCurrencySymbol() {
        try {
            Currency currency = Currency.getInstance(locale);
            return currency.getSymbol(locale);
        } catch (Exception e) {
            return NumberFormat.getCurrencyInstance(locale).getCurrency().getSymbol(locale);
        }
    }
}
