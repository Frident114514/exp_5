package edu.szu.moments.posts;

import java.util.List;

/** null 表示 PATCH 省略字段；显式 JSON null 已被解析层拒绝。 */
public record PostChange(String text, List<Long> mediaIds, List<String> tags) {}

