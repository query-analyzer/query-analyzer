package io.queryanalyzer.benchmark.capture;

import io.queryanalyzer.core.model.QueryInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-local sink that collects the SQL trace for a single benchmark scenario.
 *
 * <p>The same captured {@link QueryInfo} list is later handed, unchanged, to every
 * detector under comparison. Capturing once and replaying to all detectors keeps the
 * input identical across tools, which is what makes the precision/recall numbers
 * directly comparable.</p>
 */
public final class CapturedQueries {

    private static final ThreadLocal<List<QueryInfo>> SINK = new ThreadLocal<>();

    private CapturedQueries() {
    }

    /** Begin capturing on the current thread, discarding anything from a previous run. */
    public static void start() {
        SINK.set(new ArrayList<>());
    }

    static boolean isActive() {
        return SINK.get() != null;
    }

    static void add(QueryInfo query) {
        List<QueryInfo> sink = SINK.get();
        if (sink != null) {
            sink.add(query);
        }
    }

    /** Stop capturing and return the (immutable) trace recorded since {@link #start()}. */
    public static List<QueryInfo> stop() {
        List<QueryInfo> sink = SINK.get();
        SINK.remove();
        return sink == null ? List.of() : Collections.unmodifiableList(sink);
    }
}
