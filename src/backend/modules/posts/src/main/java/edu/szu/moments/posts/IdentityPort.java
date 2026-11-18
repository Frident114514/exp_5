package edu.szu.moments.posts;

import edu.szu.moments.posts.PostModels.Actor;

/** M01 实现：身份只能来自已验证的登录上下文，不能从请求正文读取。 */
public interface IdentityPort {
    /** 无有效登录时抛出 PostFailure.unauthenticated()。 */
    Actor requireActor();

    /** 加入当前事务，锁定用户行并复核 ACTIVE、角色和令牌版本。 */
    void lockActiveActor(Actor actor);
}

