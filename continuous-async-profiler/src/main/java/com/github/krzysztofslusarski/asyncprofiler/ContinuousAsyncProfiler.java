/*
 * Copyright 2020 Krzysztof Slusarski, Michal Rowicki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.krzysztofslusarski.asyncprofiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import one.profiler.AsyncProfiler;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.StringUtils;

@Slf4j
public class ContinuousAsyncProfiler implements DisposableBean {
    private final List<ScheduledFuture<?>> scheduledFutures = new ArrayList<>();
    private final ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(4);

    public ContinuousAsyncProfiler(ContinuousAsyncProfilerProperties properties) {
        log.info("Staring with configuration: {}", properties);

        if (!properties.isEnabled()) {
            return;
        }

        createOutputDirectories(properties);
        AsyncProfiler asyncProfiler = StringUtils.isEmpty(properties.getProfilerLibPath()) ?
                AsyncProfiler.getInstance() : AsyncProfiler.getInstance(properties.getProfilerLibPath());

        log.info("Starting continuous profiling threads");
        scheduledFutures.add(executorService.scheduleAtFixedRate(
                new ContinuousAsyncProfilerRunner(asyncProfiler, properties), 0, properties.getDumpIntervalSeconds(), TimeUnit.SECONDS
        ));
        scheduledFutures.add(executorService.scheduleAtFixedRate(
                new ContinuousAsyncProfilerCleaner(properties), 0, 1, TimeUnit.HOURS
        ));
        scheduledFutures.add(executorService.scheduleAtFixedRate(
                new ContinuousAsyncProfilerArchiver(properties), 0, 1, TimeUnit.DAYS
        ));
        scheduledFutures.add(executorService.scheduleAtFixedRate(
                new ContinuousAsyncProfilerCompressor(properties), 0, 10, TimeUnit.MINUTES
        ));
    }

    private void createOutputDirectories(ContinuousAsyncProfilerProperties properties) {
        try {
            log.debug("Checking if output dirs exist");
            Files.createDirectories(Paths.get(properties.getArchiveOutputDir()));
            Files.createDirectories(Paths.get(properties.getContinuousOutputDir()));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create output dirs", e);
        }
    }

    @Override
    public void destroy() {
        log.info("Spring context destroyed, shutting down threads");
        scheduledFutures.forEach(scheduledFuture -> scheduledFuture.cancel(true));
    }
}