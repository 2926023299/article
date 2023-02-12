package com.heima.schedule.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.common.constants.ScheduleConstants;
import com.heima.common.exception.CustomException;
import com.heima.common.redis.CacheService;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.schedule.dto.Task;
import com.heima.model.schedule.pojos.Taskinfo;
import com.heima.model.schedule.pojos.TaskinfoLogs;
import com.heima.schedule.mapper.TaskinfoLogsMapper;
import com.heima.schedule.mapper.TaskinfoMapper;
import com.heima.schedule.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;

@Service
@Transactional
@Slf4j
public class TaskServiceImpl implements TaskService {

    @Resource
    private TaskinfoMapper taskinfoMapper;

    @Resource
    private TaskinfoLogsMapper taskinfoLogsMapper;

    @Resource
    private CacheService cacheService;

    /**
     * 添加延迟任务
     * @param task
     * @return
     */
    @Override
    public long addTask(Task task) {
        //添加任务到数据库中
        boolean success = addTaskToDb(task);

        //添加任务到redis中
        if (success) {
            addTaskToRedis(task);
        }

        return task.getTaskId();
    }

    /**
     * 添加任务到redis中
     * @param task
     * @return
     */
    private void addTaskToRedis(Task task) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 5);
        long timeInMillis = calendar.getTimeInMillis();

        //如果任务的执行时间小于当前时间，存入list中
        if (task.getExecuteTime() <= System.currentTimeMillis()) {
            cacheService.lLeftPush(ScheduleConstants.TOPIC + task.getTaskType() + "_" + task.getPriority(), JSON.toJSONString(task));
        } else if (task.getExecuteTime() <= timeInMillis) {
            //如果任务的执行时间大于当前时间 && 小于等于预设时间（未来5分钟），存入zSet中
            cacheService.zAdd(ScheduleConstants.FUTURE + task.getTaskType() + "_" + task.getPriority(), JSON.toJSONString(task), task.getExecuteTime());
        } else {
            log.error("任务执行时间超过预设时间，任务id：{}", task.getTaskId());
        }
    }

    /**
     * 添加任务到数据库中
     * @param task
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    boolean addTaskToDb(Task task) {
        try {
            //保存任务表
            Taskinfo taskinfo = new Taskinfo();
            BeanUtils.copyProperties(task, taskinfo);
            taskinfo.setExecuteTime(new Date(task.getExecuteTime()));
            taskinfoMapper.insert(taskinfo);

            //设置任务id
            task.setTaskId(taskinfo.getTaskId());

            //保存任务日志表
            TaskinfoLogs taskinfoLogs = new TaskinfoLogs();
            BeanUtils.copyProperties(task, taskinfoLogs);
            taskinfoLogs.setStatus(ScheduleConstants.SCHEDULED);
            taskinfoLogs.setExecuteTime(new Date(task.getExecuteTime()));
            taskinfoLogs.setVersion(1);
            taskinfoLogsMapper.insert(taskinfoLogs);
        } catch (Exception e) {
            log.error("添加任务到数据库中失败", e);
            return false;
        }

        return true;
    }

    /**
     * 取消延迟任务
     * @param taskId
     * @return
     */
    @Override
    public boolean cancelTask(long taskId) {
        //删除任务,更新任务日志
        Task task = updateDB(taskId, ScheduleConstants.CANCELLED);

        //删除redis数据
        if (task != null) {
            try {
                deleteRedis(task);
            } catch (Exception e) {
                log.error("删除redis数据失败", e);
            }
        } else {
            return false;
        }

        return true;
    }

    /**
     * 按照类型和优先级拉取任务
     * @param type
     * @param priority
     * @return
     */
    @Override
    public Task pull(int type, int priority) {
        //从redis中拉取任务
        Task task = new Task();
        try {
            String taskJson = cacheService.lRightPop(ScheduleConstants.TOPIC + type + "_" + priority);

            if (StringUtils.isNotBlank(taskJson)) {
                task = JSON.parseObject(taskJson, Task.class);

                //修改数据库信息
                updateDB(task.getTaskId(), ScheduleConstants.EXECUTED);

                return task;
            }
        } catch (Exception e) {
            log.error("从redis中拉取任务失败", e);
        }

        return null;
    }

    private void deleteRedis(Task task) {

        String key = task.getTaskType() + "_" + task.getPriority();

        if (task.getExecuteTime() <= System.currentTimeMillis()) {
            //删除list中的数据
            cacheService.lRemove(ScheduleConstants.TOPIC + key, 0, JSON.toJSONString(task));
        } else {
            //删除zSet中的数据
            cacheService.zRemove(ScheduleConstants.FUTURE + key, JSON.toJSONString(task));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    Task updateDB(long taskId, int status) {
        Task task = null;
        try {
            taskinfoMapper.deleteById(taskId);

            TaskinfoLogs taskinfoLogs = taskinfoLogsMapper.selectById(taskId);
            taskinfoLogs.setStatus(status);
            taskinfoLogsMapper.updateById(taskinfoLogs);

            task = new Task();
            BeanUtils.copyProperties(taskinfoLogs, task);
            task.setExecuteTime(taskinfoLogs.getExecuteTime().getTime());
        } catch (BeansException e) {
            log.error("更新任务日志失败", e);
        }

        return task;
    }

    /**
     * 未来数据定时刷新
     */
    @Scheduled(cron = "0 */1 * * * ?")
    public void refresh() {
        String token = cacheService.tryLock("FUTURE_TASK_SYNC", 1000 * 30);
        if (StringUtils.isNotBlank(token)) {
            log.info("未来数据定时刷新---定时任务");

            //获取所有未来数据的集合key
            Set<String> futureKeys = cacheService.scan(ScheduleConstants.FUTURE + "*");
            for (String futureKey : futureKeys) {//future_100_50

                //获取当前数据的key  topic
                String topicKey = ScheduleConstants.TOPIC + futureKey.split(ScheduleConstants.FUTURE)[1];

                //按照key和分值查询符合条件的数据
                Set<String> tasks = cacheService.zRangeByScore(futureKey, 0, System.currentTimeMillis());

                //同步数据
                if (!tasks.isEmpty()) {
                    cacheService.refreshWithPipeline(futureKey, topicKey, tasks);
                    log.info("成功的将" + futureKey + "刷新到了" + topicKey);
                }
            }
        }
    }

    /**
     * 数据库任务定时同步到redis
     */
    @Scheduled(cron = "0 */5 * * * ?")
    @PostConstruct
    public void reloadData() {
        clearCache();
        log.info("数据库数据同步到缓存");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 5);

        //查看小于未来5分钟的所有任务
        List<Taskinfo> allTasks = taskinfoMapper.selectList(Wrappers.<Taskinfo>lambdaQuery().lt(Taskinfo::getExecuteTime, calendar.getTime()));
        if (allTasks != null && allTasks.size() > 0) {
            for (Taskinfo taskinfo : allTasks) {
                Task task = new Task();
                BeanUtils.copyProperties(taskinfo, task);
                task.setExecuteTime(taskinfo.getExecuteTime().getTime());
                addTaskToRedis(task);
            }
        }
    }

    /**
     * 清理缓存中的数据
     */
    public void clearCache() {
        //清理缓存中数据
        Set<String> topicKeys = cacheService.scan(ScheduleConstants.TOPIC + "*");
        Set<String> futureKeys = cacheService.scan(ScheduleConstants.FUTURE + "*");

        cacheService.delete(topicKeys);
        cacheService.delete(futureKeys);
    }

}
