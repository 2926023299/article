package com.heima.schedule.service.impl;

import com.heima.model.schedule.dto.Task;
import com.heima.schedule.ScheduleApplication;
import com.heima.schedule.service.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ScheduleApplication.class)
@RunWith(SpringRunner.class)
class TaskServiceImplTest {

    @Autowired
    private TaskService taskService;

    @Test
    void addTask() {
            Task task = new Task();
            task.setTaskType(100);
            task.setPriority(50);
            task.setParameters("test task".getBytes());
            task.setExecuteTime(new Date().getTime() + 50000);
            long taskId = taskService.addTask(task);
            System.out.println(taskId);
    }

    @Test
    void cancelTask() {
        boolean b = taskService.cancelTask(1580074642139332609L);
        System.out.println(b);
    }

    @Test
    public void pull() {
        Task pull = taskService.pull(100, 50);
        System.out.println(pull);
    }
}