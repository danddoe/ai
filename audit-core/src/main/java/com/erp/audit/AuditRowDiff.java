package com.erp.audit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Generic before/after diff for map-shaped row snapshots (core master data, import jobs, etc.).
 * Entity-record field audits in entity-builder use slug-based paths instead.
 */
public final class AuditRowDiff {

    private AuditRowDiff() {
    }

    public static List<Map<String, Object>> diffRow(Map<String, Object> before, Map<String, Object> after) {
        Set<String> keys = new TreeSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        List<Map<String, Object>> out = new ArrayList<>();
        for (String k : keys) {
            Object o = before.get(k);
            Object n = after.get(k);
            if (valuesEqual(o, n)) {
                continue;
            }
            Map<String, Object> ch = new LinkedHashMap<>();
            ch.put("path", "row." + k);
            ch.put("old", o);
            ch.put("new", n);
            out.add(ch);
        }
        return out;
    }

    public static boolean valuesEqual(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof BigDecimal ad && b instanceof BigDecimal bd) {
            return ad.compareTo(bd) == 0;
        }
        return Objects.equals(a, b);
    }
}
