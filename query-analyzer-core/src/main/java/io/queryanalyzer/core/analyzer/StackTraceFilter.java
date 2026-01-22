package io.queryanalyzer.core.analyzer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

// Filters stack traces to remove framework internals and highlight application code.
public final class StackTraceFilter {

    private static final Set<String> IGNORED_PACKAGES = Set.of(
            // JDK internals
            "java.",
            "javax.",
            "jdk.",
            "sun.",

            // Jakarta EE
            "jakarta.",

            // Hibernate/JPA
            "org.hibernate",

            // Spring Framework
            "org.springframework.aop",
            "org.springframework.cglib",
            "org.springframework.transaction",
            "org.springframework.jdbc",
            "org.springframework.web.servlet",
            "org.springframework.web.method",
            "org.springframework.data.jpa",
            "org.springframework.data.repository",
            "org.springframework.data.util",
            "org.springframework.data.projection",
            "org.springframework.orm",
            "org.springframework.dao",  // PersistenceExceptionTranslationInterceptor
            "org.springframework.beans",
            "org.springframework.context",
            
            // Jackson (JSON serialization) - causes false location detection
            "com.fasterxml.jackson",

            // Database drivers
            "com.mysql.cj",
            "org.postgresql",
            "com.zaxxer.hikari",
            "org.apache.tomcat.jdbc",
            "org.h2",

            // Proxies
            "com.sun.proxy",
            "net.sf.cglib",
            "$Proxy",

            // Web servers
            "org.apache.tomcat",
            "org.apache.catalina",
            "io.undertow",
            "org.eclipse.jetty",

            // Query Analyzer internals
            "io.queryanalyzer.core.tracker",
            "io.queryanalyzer.spring.interceptor"
    );

    private StackTraceFilter() {
    }

    public static StackTraceElement[] filter(StackTraceElement[] stackTrace) {
        if (stackTrace == null || stackTrace.length == 0) {
            return new StackTraceElement[0];
        }

        List<StackTraceElement> filtered = new ArrayList<>();

        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();

            if (isApplicationCode(element)) {
                filtered.add(element);
            }
            else if (className.contains("$$") || className.contains("$Proxy")) {
                filtered.add(element);
            }
        }

        if (filtered.isEmpty()) {
            return stackTrace;
        }

        return filtered.toArray(new StackTraceElement[0]);
    }

    public static String findApplicationCode(StackTraceElement[] stackTrace) {
        if (stackTrace == null || stackTrace.length == 0) {
            return "unknown";
        }

        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();

            if (className.contains("$$") || className.contains("$Proxy") ||
                    className.contains("EnhancerBySpringCGLIB")) {
                continue;
            }

            if (isApplicationCode(element)) {
                return formatLocation(element);
            }
        }

        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (className != null && (
                    className.contains(".repository.") ||
                            className.contains(".service.") ||
                            className.contains(".controller.")
            )) {
                return formatLocation(element);
            }
        }

        return "unknown";
    }

    private static boolean isApplicationCode(StackTraceElement element) {
        if (element == null) {
            return false;
        }

        String className = element.getClassName();

        for (String ignoredPackage : IGNORED_PACKAGES) {
            if (className.startsWith(ignoredPackage)) {
                return false;
            }
        }

        if (className.contains("$$") ||
                className.contains("$Proxy") ||
                className.contains("EnhancerBySpringCGLIB")) {
            return false;
        }

        return true;
    }

    private static String formatLocation(StackTraceElement element) {
        String className = element.getClassName();
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);

        return String.format("%s.%s:%d",
                simpleClassName,
                element.getMethodName(),
                element.getLineNumber());
    }
}