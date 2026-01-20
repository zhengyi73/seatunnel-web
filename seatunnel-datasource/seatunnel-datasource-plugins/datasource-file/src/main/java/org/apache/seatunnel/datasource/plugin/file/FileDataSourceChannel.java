/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.datasource.plugin.file;

import org.apache.seatunnel.api.configuration.util.OptionRule;
import org.apache.seatunnel.datasource.plugin.api.DataSourceChannel;
import org.apache.seatunnel.datasource.plugin.api.model.TableField;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FileDataSourceChannel implements DataSourceChannel {

    @Override
    public OptionRule getDataSourceOptions(String pluginName) {
        return FileOptionRule.optionRule();
    }

    @Override
    public OptionRule getDatasourceMetadataFieldsByDataSourceName(String pluginName) {
        return FileOptionRule.metadataRule();
    }

    @Override
    public List<String> getTables(
            String pluginName,
            Map<String, String> requestParams,
            String database,
            Map<String, String> options) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getDatabases(String pluginName, Map<String, String> requestParams) {
        return Collections.emptyList();
    }

    @Override
    public boolean checkDataSourceConnectivity(
            String pluginName, Map<String, String> requestParams) {
        return true;
    }

    @Override
    public List<TableField> getTableFields(
            String pluginName, Map<String, String> requestParams, String database, String table) {
        return Collections.emptyList();
    }
}
