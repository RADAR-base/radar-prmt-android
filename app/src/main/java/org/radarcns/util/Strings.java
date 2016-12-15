package org.radarcns.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Pattern;

public class Strings {
    public static Pattern[] containsPatterns(Collection<String> contains) {
        Pattern[] patterns = new Pattern[contains.size()];
        Iterator<String> containsIterator = contains.iterator();
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = containsIgnoreCasePattern(containsIterator.next());
        }
        return patterns;
    }

    public static Pattern containsIgnoreCasePattern(String containsString) {
        int flags = Pattern.CASE_INSENSITIVE // case insensitive
                | Pattern.LITERAL // do not compile special characters
                | Pattern.UNICODE_CASE; // case insensitive even for Unicode (special) characters.
        return Pattern.compile(containsString, flags);
    }

    public static boolean findAny(Pattern[] patterns, CharSequence value) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(value).find()) {
                return true;
            }
        }
        return false;
    }
}
