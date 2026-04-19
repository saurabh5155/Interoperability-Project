package com.healthcare.interop.common.util;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders payload templates by substituting {{placeholders}} with values from a context map.
 * Used by dynamic-adapter-service to build EHR-specific request bodies.
 *
 * Throws {@link IllegalArgumentException} when any placeholder has no corresponding
 * context entry, so callers receive an explicit error rather than a silently malformed payload.
 */
@UtilityClass
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    /**
     * Renders the template, substituting all {{key}} placeholders from the context map.
     *
     * @throws IllegalArgumentException if any placeholder key is absent from the context
     */
    public static String render(String template, Map<String, String> context) {
        if (template == null) return null;

        List<String> missing = new ArrayList<>();
        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);

        while (matcher.find()) {
            String key = matcher.group(1).trim();
            if (!context.containsKey(key)) {
                missing.add(key);
                matcher.appendReplacement(result, "");
            } else {
                String value = context.get(key);
                matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : value));
            }
        }
        matcher.appendTail(result);

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                "Template has unresolved placeholders: " + missing +
                ". Available keys: " + context.keySet());
        }

        return result.toString();
    }

    public static boolean hasUnresolvedPlaceholders(String rendered) {
        return PLACEHOLDER_PATTERN.matcher(rendered).find();
    }
}
