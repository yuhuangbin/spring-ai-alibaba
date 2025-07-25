/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.graph.internal.node;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.async.AsyncGenerator;
import com.alibaba.cloud.ai.graph.async.internal.reactive.GeneratorSubscriber;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;

public class ParallelNode extends Node {

	public static final String PARALLEL_PREFIX = "__PARALLEL__";

	record AsyncParallelNodeAction(List<AsyncNodeActionWithConfig> actions,
			Map<String, KeyStrategy> channels) implements AsyncNodeActionWithConfig {

		@Override
		public CompletableFuture<Map<String, Object>> apply(OverAllState state, RunnableConfig config) {
			// 使用线程安全的ConcurrentHashMap来避免并发问题
			Map<String, Object> partialMergedStates = new java.util.concurrent.ConcurrentHashMap<>();
			Map<String, Object> asyncGenerators = new java.util.concurrent.ConcurrentHashMap<>();
			var futures = actions.stream().map(action -> action.apply(state, config).thenApply(partialState -> {
				partialState.forEach((key, value) -> {
					if (value instanceof AsyncGenerator<?> || value instanceof GeneratorSubscriber) {
						((List) asyncGenerators.computeIfAbsent(key, k -> new ArrayList<>())).add(value);
					}
					else {
						// 修复：使用KeyStrategy正确合并状态，而不是直接覆盖
						KeyStrategy strategy = channels.get(key);
						if (strategy != null) {
							// 使用原子操作来确保线程安全
							partialMergedStates.compute(key, (k, existingValue) -> {
								return strategy.apply(existingValue, value);
							});
						}
						else {
							// 如果没有配置KeyStrategy，使用默认的替换策略
							partialMergedStates.put(key, value);
						}
					}
				});
				// 移除这行：不在每个并行节点完成后立即更新状态
				// state.updateState(partialMergedStates);
				return action;
			}))
				// .map( future -> supplyAsync(future::join) )
				.toList()
				.toArray(new CompletableFuture[0]);
			return CompletableFuture.allOf(futures).thenApply((p) -> {
				// 在所有并行节点完成后，统一更新状态
				if (!CollectionUtils.isEmpty(partialMergedStates)) {
					state.updateState(partialMergedStates);
				}
				return CollectionUtils.isEmpty(asyncGenerators) ? state.data() : asyncGenerators;
			});
		}

	}

	public ParallelNode(String id, List<AsyncNodeActionWithConfig> actions, Map<String, KeyStrategy> channels) {
		super(format("%s(%s)", PARALLEL_PREFIX, id), (config) -> new AsyncParallelNodeAction(actions, channels));
	}

	@Override
	public final boolean isParallel() {
		return true;
	}

}
