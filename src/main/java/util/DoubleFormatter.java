package util;

import lombok.experimental.UtilityClass;

import java.text.DecimalFormat;

@UtilityClass
public class DoubleFormatter {
    private static final DecimalFormat DF_5 = new DecimalFormat("0.#####");
    private static final DecimalFormat DF_4 = new DecimalFormat("0.####");
    private static final DecimalFormat DF_3 = new DecimalFormat("0.###");
    private static final DecimalFormat DF_2 = new DecimalFormat("0.##");
    private static final DecimalFormat DF_1 = new DecimalFormat("0.#");
    private static final DecimalFormat DF_0 = new DecimalFormat("0");

    public static String format(double value) {
        if (value == 0) {
            return "0";
        }

        double abs = Math.abs(value);

        if (abs < 1) {
            return DF_3.format(value);
        } else if (abs < 10) {
            return DF_2.format(value);
        } else if (abs < 100) {
            return DF_1.format(value);
        } else {
            return DF_0.format(value);
        }
    }

    public static String formatPrecise(double value) {
        if (value == 0) {
            return "0";
        }

        double abs = Math.abs(value);
        if (abs < 1) {
            return DF_5.format(value);
        } else if (abs < 10) {
            return DF_4.format(value);
        } else if (abs < 100) {
            return DF_3.format(value);
        } else {
            return DF_2.format(value);
        }
    }
}
