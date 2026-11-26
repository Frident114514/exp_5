package edu.szu.moments.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/** Opt-in, bounded experiment data; no accounts, tokens or per-request disk logging. */
@Component
public final class M01Diagnostics {
    public static final String ATTRIBUTE = M01Diagnostics.class.getName();
    private static final int MAX_TRIALS = 16;
    private static final int MAX_REQUESTS = 60000;
    private final boolean enabled;
    private final long origin = System.nanoTime();
    private final Map<String, Trial> trials = new ConcurrentHashMap<>();
    private final LongAdder dropped = new LongAdder();
    private final ThreadLocal<Observation> current = new ThreadLocal<>();

    public M01Diagnostics(@Value("${moments.diagnostics.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    private long now() { return System.nanoTime() - origin; }

    public Observation begin(HttpServletRequest request) {
        if (!enabled) return null;
        String name = request.getHeader("X-M01-Trial");
        String id = request.getHeader("X-M01-Request-Id");
        if (name == null || !name.matches("[a-zA-Z0-9_-]{1,40}") || id == null || !id.matches("[0-9]{1,5}")) return null;
        int index = Integer.parseInt(id);
        if (index >= MAX_REQUESTS) { dropped.increment(); return null; }
        Trial trial = trials.get(name);
        if (trial == null) {
            synchronized (trials) {
                trial = trials.get(name);
                if (trial == null) {
                    if (trials.size() >= MAX_TRIALS) { dropped.increment(); return null; }
                    trial = new Trial();
                    trials.put(name, trial);
                }
            }
        }
        var observation = new Observation(trial, index, now());
        trial.entered.increment();
        trial.peak.accumulateAndGet(trial.active.incrementAndGet(), Math::max);
        request.setAttribute(ATTRIBUTE, observation);
        current.set(observation);
        return observation;
    }

    public void admitted(Observation observation) {
        if (observation == null) return;
        observation.admittedAt = now();
        observation.trial.admitted.increment();
        observation.trial.admittedPeak.accumulateAndGet(observation.trial.admittedActive.incrementAndGet(), Math::max);
    }

    public void released(Observation observation) {
        if (observation == null || observation.admittedAt < 0 || observation.releasedAt >= 0) return;
        observation.releasedAt = now();
        observation.trial.admittedActive.decrementAndGet();
    }

    public void reason(HttpServletRequest request, String reason) {
        if (request.getAttribute(ATTRIBUTE) instanceof Observation observation) observation.reason = reason;
    }

    /** Includes pool acquisition, SQL and mapping; deliberately not labelled pure SQL time. */
    public <T> T databaseLookup(Supplier<T> lookup) {
        Observation observation = current.get();
        if (observation == null) return lookup.get();
        observation.databaseStart = now();
        try { return lookup.get(); }
        finally { observation.databaseEnd = now(); }
    }

    public void finish(Observation observation, int status, boolean aborted) {
        if (observation == null) return;
        try {
            String reason = status == 503 ? observation.reason : "none";
            if (reason == null) reason = "other_503";
            long end = now();
            var trace = new Trace(observation.id, observation.start, observation.admittedAt, observation.releasedAt,
                    observation.databaseStart, observation.databaseEnd, end, status, reason, aborted);
            if (observation.trial.traces.getAndSet(observation.id, trace) != null) observation.trial.duplicates.increment();
            observation.trial.completed.increment();
            if (status == 503) observation.trial.reasons.computeIfAbsent(reason, key -> new LongAdder()).increment();
            observation.trial.active.decrementAndGet();
        } finally { current.remove(); }
    }

    public Map<String, Object> snapshot(String name) {
        Trial trial = trials.get(name);
        if (trial == null) return Map.of("found", false, "dropped", dropped.sum());
        var traces = new ArrayList<Trace>();
        for (int i = 0; i < MAX_REQUESTS; i++) {
            Trace trace = trial.traces.get(i);
            if (trace != null) traces.add(trace);
        }
        var reasons = new LinkedHashMap<String, Long>();
        trial.reasons.forEach((key, count) -> reasons.put(key, count.sum()));
        return Map.ofEntries(Map.entry("found", true), Map.entry("trial", name),
                Map.entry("clock", "server monotonic nanoseconds since diagnostics initialization"),
                Map.entry("interval", "application filter entry to exit, not network arrival to final byte"),
                Map.entry("entered", trial.entered.sum()), Map.entry("admitted", trial.admitted.sum()),
                Map.entry("completed", trial.completed.sum()), Map.entry("active", trial.active.get()),
                Map.entry("peakFilterActive", trial.peak.get()), Map.entry("peakAdmittedActive", trial.admittedPeak.get()),
                Map.entry("duplicates", trial.duplicates.sum()), Map.entry("dropped", dropped.sum()),
                Map.entry("reasons503", reasons), Map.entry("traces", traces));
    }

    public static final class Observation {
        private final Trial trial;
        private final int id;
        private final long start;
        private long admittedAt = -1, releasedAt = -1, databaseStart = -1, databaseEnd = -1;
        private String reason;
        private Observation(Trial trial, int id, long start) { this.trial = trial; this.id = id; this.start = start; }
    }

    public record Trace(int id, long filterEnterNs, long admittedNs, long releaseObservedNs, long databaseStartNs,
                        long databaseEndNs, long filterExitNs, int status, String reason503, boolean aborted) {}

    private static final class Trial {
        final AtomicReferenceArray<Trace> traces = new AtomicReferenceArray<>(MAX_REQUESTS);
        final LongAdder entered = new LongAdder(), admitted = new LongAdder(), completed = new LongAdder(), duplicates = new LongAdder();
        final AtomicInteger active = new AtomicInteger(), peak = new AtomicInteger(), admittedActive = new AtomicInteger(), admittedPeak = new AtomicInteger();
        final Map<String, LongAdder> reasons = new ConcurrentHashMap<>();
    }
}
