package org.jenkinsci.plugins.parameterpool;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class ParameterParser {

    private final Matcher rangeMatcher = Pattern.compile("(.+)\\[(\\d+)\\.\\.(\\d+)\\](.*)").matcher("");

    private Set<String> values = new LinkedHashSet<String>();

    public ParameterParser(String sourceText) {
        parseValues(sourceText == null ? "" : sourceText);
    }

    private void parseValues(String sourceText) {
        String[] pieces = sourceText.split(",");
        for (String piece : pieces) {
            piece = piece.trim();
            rangeMatcher.reset(piece);
            if (!rangeMatcher.find()) {
                values.add(piece);
                continue;
            }

            addValuesForPattern();
        }
    }

    private void addValuesForPattern() {
        String prefix = rangeMatcher.group(1);
        int start = Integer.parseInt(rangeMatcher.group(2));
        int end = Integer.parseInt(rangeMatcher.group(3));
        String suffix = rangeMatcher.group(4);

        if (start > end) {
            for (int i = start; i >= end; i --) {
                values.add(prefix + i + suffix);
            }
        } else {
            for (int i = start; i <= end; i ++) {
                values.add(prefix + i + suffix);
            }
        }
    }

    public Set<String> getValues() {
        return values;
    }

    public String valuesAsText() {
        StringBuilder valuesText = new StringBuilder();
        for (String value : values) {
            if (valuesText.length() > 0) {
                valuesText.append(", ");
            }

            valuesText.append(value);
        }
        return valuesText.toString();
    }
}
