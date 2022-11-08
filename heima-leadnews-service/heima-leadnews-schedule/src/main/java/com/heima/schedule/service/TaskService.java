package com.heima.schedule.service;

import com.heima.model.schedule.dto.Task;
import org.apache.ibatis.annotations.Param;

public interface TaskService {

    /**
     * 添加延迟任务
     * @param task
     * @return
     */
    public long addTask(Task task);

    /**
     * 取消延迟任务
     */
    public boolean cancelTask(long taskId);

    /**
     * 按照类型和优先级拉取任务
     * @param type
     * @param priority
     * @return
     */
    public Task pull(int type, int priority);
}
