package nu.studer.gradle.rocker;

final class StringUtils {

    static String capitalize(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private StringUtils() {
    }

}
