package edu.szu.moments.posts;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.szu.moments.posts.PostModels.MediaCandidate;
import edu.szu.moments.posts.PostModels.MediaView;

/** M03 实现；写操作须与动态共用数据源和当前事务。 */
public interface MediaPort {
    /** 按 ID 升序锁行，返回全部请求 ID 对应的真实元数据；缺失项允许省略。 */
    List<MediaCandidate> lockAndInspect(List<Long> mediaIds);

    /** 替换为给定顺序的媒体；移除项解除绑定。失败必须抛异常，不得单独提交。 */
    void replaceBindings(long actorId, long postId, List<Long> mediaIds, Instant now);

    /** 批量查询，每个请求的 postId 都有一项（无媒体为 []），保留媒体排序。 */
    Map<Long, List<MediaView>> findByPostIds(Set<Long> postIds);
}

