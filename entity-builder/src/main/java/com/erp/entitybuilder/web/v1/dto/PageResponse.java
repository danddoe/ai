package com.erp.entitybuilder.web.v1.dto;

import java.util.List;

public class PageResponse<T> {
    private final List<T> items;
    private final int page;
    private final int pageSize;
    private final long total;

    public PageResponse(List<T> items, int page, int pageSize, long total) {
        this.items = items;
        this.page = page;
        this.pageSize = pageSize;
        this.total = total;
    }

    public List<T> getItems() { return items; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public long getTotal() { return total; }
}

