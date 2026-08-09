package dev.signaldock.subscription;

import dev.signaldock.exception.InvalidRequestException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class EventPatternMatcher {
    private static final Pattern VALID = Pattern.compile("^[a-zA-Z0-9_-]+(?:\\.[a-zA-Z0-9_-]+)*(?:\\.\\*)?$|^\\*$" );

    public void validate(String pattern) {
        if (!VALID.matcher(pattern).matches()) {
            throw new InvalidRequestException("Event pattern must be exact (order.created), a trailing wildcard (order.*), or *.");
        }
    }

    public boolean matches(String pattern, String eventType) {
        String normalizedPattern = pattern.toLowerCase(Locale.ROOT);
        String normalizedType = eventType.toLowerCase(Locale.ROOT);
        if ("*".equals(normalizedPattern)) {
            return true;
        }
        if (normalizedPattern.endsWith(".*")) {
            String prefix = normalizedPattern.substring(0, normalizedPattern.length() - 1);
            return normalizedType.startsWith(prefix) && normalizedType.length() > prefix.length();
        }
        return normalizedPattern.equals(normalizedType);
    }
}

