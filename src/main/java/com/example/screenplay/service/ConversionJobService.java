package com.example.screenplay.service;

import com.example.screenplay.model.ConversionJob;
import com.example.screenplay.model.ConversionRequest;
import com.example.screenplay.model.GenerationProgress;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class ConversionJobService {

    private final ConversionService conversionService;
    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ConversionJobService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    public ConversionJob start(ConversionRequest request) {
        String id = UUID.randomUUID().toString();
        JobState state = new JobState(id);
        jobs.put(id, state);
        state.task = executor.submit(() -> run(request, state));
        return state.snapshot();
    }

    public ConversionJob get(String id) {
        JobState state = jobs.get(id);
        if (state == null) {
            throw new IllegalArgumentException("转换任务不存在或已失效");
        }
        return state.snapshot();
    }

    public ConversionJob cancel(String id) {
        JobState state = jobs.get(id);
        if (state == null) {
            throw new IllegalArgumentException("转换任务不存在或已失效");
        }
        state.cancel();
        return state.snapshot();
    }

    private void run(ConversionRequest request, JobState state) {
        if (state.cancelled) {
            return;
        }
        state.status = "RUNNING";
        try {
            var result = conversionService.convert(request, state::update);
            if (state.cancelled) {
                return;
            }
            state.result = result;
            state.status = "COMPLETED";
        } catch (Exception exception) {
            if (state.cancelled || Thread.currentThread().isInterrupted()) {
                state.cancel();
                return;
            }
            state.status = "FAILED";
            state.error = exception.getMessage();
            state.message = "处理失败";
        }
    }

    private static final class JobState {
        private final String id;
        private volatile String status = "QUEUED";
        private volatile String stage = "source";
        private volatile String message = "任务已创建";
        private volatile int percent;
        private volatile com.example.screenplay.model.Screenplay preview;
        private volatile com.example.screenplay.model.ConversionResult result;
        private volatile String error;
        private volatile boolean cancelled;
        private volatile Future<?> task;

        private JobState(String id) {
            this.id = id;
        }

        private void update(GenerationProgress progress) {
            if (cancelled) {
                return;
            }
            stage = progress.stage();
            message = progress.message();
            percent = Math.max(percent, progress.percent());
            if (progress.preview() != null) {
                preview = progress.preview();
            }
        }

        private synchronized void cancel() {
            if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELED".equals(status)) {
                return;
            }
            cancelled = true;
            status = "CANCELED";
            message = "改编已取消";
            error = "AI 配置已切换，请重新开始改编";
            Future<?> currentTask = task;
            if (currentTask != null) {
                currentTask.cancel(true);
            }
        }

        private ConversionJob snapshot() {
            return new ConversionJob(id, status, stage, message, percent, preview, result, error);
        }
    }
}
