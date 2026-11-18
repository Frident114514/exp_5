package edu.szu.moments.posts;

import java.util.Map;
import java.util.Set;

import edu.szu.moments.posts.PostModels.RatingSummary;

/** M04 实现；一次聚合整页，每个请求 ID 都返回摘要，无评分为 null/0。 */
public interface RatingPort {
    Map<Long, RatingSummary> summarize(Set<Long> postIds);
}

