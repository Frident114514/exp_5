package edu.szu.moments.api;

import java.util.List;

public record PageData<T>(List<T> items, int page, int pageSize, long total) {}
