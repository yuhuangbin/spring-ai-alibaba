/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.connector.support;

import com.alibaba.cloud.ai.connector.AbstractDdl;
import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.enums.BizDataSourceTypeEnum;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DdlFactory {

	private static Map<String, AbstractDdl> ddlExecutorSet = new ConcurrentHashMap<>();

	public static void registry(AbstractDdl ddlExecutor) {
		ddlExecutorSet.put(getConstraint(ddlExecutor.getType()), ddlExecutor);
	}

	public AbstractDdl getDdlExecutor(DbConfig dbConfig) {
		BizDataSourceTypeEnum type = BizDataSourceTypeEnum.fromTypeName(dbConfig.getDialectType());
		if (type == null) {
			throw new RuntimeException("unknown db type");
		}
		return getDdlExecutor(type);
	}

	public AbstractDdl getDdlExecutor(BizDataSourceTypeEnum type) {
		return ddlExecutorSet.get(getConstraint(type));
	}

	private static String getConstraint(BizDataSourceTypeEnum type) {
		return type.getProtocol() + "@" + type.getDialect();
	}

}
