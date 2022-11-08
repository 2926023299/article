package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSON;
import com.heima.api.schedule.IScheduleClient;
import com.heima.common.constants.TaskTypeEnum;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.schedule.dto.Task;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.utils.common.ProtostuffUtil;
import com.heima.wemedia.service.WmNewsTaskService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@Slf4j
public class WmNewsTaskServiceImpl implements WmNewsTaskService {

    @Autowired
    private IScheduleClient scheduleClient;

    /**
     * 添加任务到延迟队列中
     *
     * @param id          文章id
     * @param publishTime 发布时间
     */

    @Async
    @Override
    public void addNewsToTask(Integer id, Date publishTime) {

        log.info("添加任务到延迟队列中，文章id：{}，发布时间：{}", id, publishTime);

        Task task = new Task();
        task.setExecuteTime(publishTime.getTime());
        task.setPriority(TaskTypeEnum.NEWS_SCAN_TIME.getPriority());
        task.setTaskType(TaskTypeEnum.NEWS_SCAN_TIME.getTaskType());

        WmNews wmNews = new WmNews();
        wmNews.setId(id);
        task.setParameters(ProtostuffUtil.serialize(wmNews));

        scheduleClient.addTask(task);
    }

    @Autowired
    private WmNewsAutoScanServiceImpl wmNewsAutoScanService;

    /**
     * 消费延迟队列数据
     */
    @Scheduled(fixedRate = 1000)
    @Override
    @SneakyThrows
    public void scanNewsByTask() {
        ResponseResult responseResult = scheduleClient.pull(TaskTypeEnum.NEWS_SCAN_TIME.getTaskType(), TaskTypeEnum.NEWS_SCAN_TIME.getPriority());

        if (responseResult.getCode().equals(200) && responseResult.getData() != null) {
            log.info("文章审核---消费任务执行---begin---");
            String json_str = JSON.toJSONString(responseResult.getData());
            Task task = JSON.parseObject(json_str, Task.class);
            byte[] parameters = task.getParameters();
            WmNews wmNews = ProtostuffUtil.deserialize(parameters, WmNews.class);
            System.out.println(wmNews.getId() + "-----------");
            wmNewsAutoScanService.autoScanWmNews(wmNews);

            log.info("文章审核---消费任务执行---end---");
        }

    }
}
