package com.erp.entitybuilder.web.v1.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

/**
 * Request body for {@code POST /v1/tenants/{tenantId}/entities/{entityId}/records/query}.
 * <p>
 * Filter: either a <strong>group</strong> ({@code op} is {@code and} or {@code or}, {@code children} non-empty)
 * or a <strong>clause</strong> ({@code field} slug + {@code op} + optional {@code value}).
 * Omit {@code filter} entirely to match all records (same as unpaginated list semantics).
 */
public class RecordQueryDtos {

    public static class RecordQueryRequest {
        @Valid
        private FilterNode filter;
        @Valid
        private RecordSort sort;
        @Min(1)
        private int page = 1;
        @Min(1)
        @Max(200)
        private int pageSize = 50;

        public FilterNode getFilter() {
            return filter;
        }

        public void setFilter(FilterNode filter) {
            this.filter = filter;
        }

        public RecordSort getSort() {
            return sort;
        }

        public void setSort(RecordSort sort) {
            this.sort = sort;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }
    }

    /**
     * Sort by {@code entity_records} columns. Allowed {@code field} values: {@code record.updated_at} (default),
     * {@code record.created_at}. {@code direction}: {@code asc} or {@code desc} (default).
     */
    public static class RecordSort {
        private String field;
        private String direction = "desc";

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }
    }

    /**
     * Raw JSON filter node; validated in {@link com.erp.entitybuilder.service.query.RecordFilterValidator}.
     */
    public static class FilterNode {
        /** Group: {@code and} / {@code or}. Clause: operator name (e.g. {@code eq}, {@code gte}). */
        private String op;
        /** Clause: field slug on the entity. */
        private String field;
        /** Clause: scalar, array for {@code between} / {@code in}, or omitted for {@code is_null} / {@code is_not_null}. */
        private JsonNode value;
        @Valid
        private List<FilterNode> children;

        public String getOp() {
            return op;
        }

        public void setOp(String op) {
            this.op = op;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public JsonNode getValue() {
            return value;
        }

        public void setValue(JsonNode value) {
            this.value = value;
        }

        public List<FilterNode> getChildren() {
            return children;
        }

        public void setChildren(List<FilterNode> children) {
            this.children = children;
        }
    }
}
