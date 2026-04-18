package com.healthcare.interop.common.util;

import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders payload templates by substituting {{placeholders}} with values from a context map.
 * Used by dynamic-adapter-service to build EHR-specific request bodies.
 */
@UtilityClass
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    public static String render(String template, Map<String, String> context) {
        if (template == null) return null;
        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = context.getOrDefault(key, "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static boolean hasUnresolvedPlaceholders(String rendered) {
        return PLACEHOLDER_PATTERN.matcher(rendered).find();
    }
}
