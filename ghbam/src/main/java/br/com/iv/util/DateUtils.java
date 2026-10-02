package br.com.iv.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class DateUtils {

    private static final List<DateTimeFormatter> INPUT_FORMATS = Arrays.asList(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    private static final DateTimeFormatter OUTPUT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    public static String toYMD(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        dateStr = dateStr.trim();
        if (dateStr.isEmpty()) {
            return dateStr;
        }

        for (DateTimeFormatter fmt : INPUT_FORMATS) {
            try {
                LocalDate ld = LocalDate.parse(dateStr, fmt);
                return ld.format(OUTPUT);
            } catch (Exception e) {
                // try next
            }
        }

        // fallback: return original
        return dateStr;
    }
}
