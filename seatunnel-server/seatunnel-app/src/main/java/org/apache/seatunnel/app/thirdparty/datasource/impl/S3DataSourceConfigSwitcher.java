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

package org.apache.seatunnel.app.thirdparty.datasource.impl;

import org.apache.seatunnel.shade.com.typesafe.config.Config;
import org.apache.seatunnel.shade.com.typesafe.config.ConfigValueFactory;

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.util.OptionRule;
import org.apache.seatunnel.api.configuration.util.RequiredOption;
import org.apache.seatunnel.app.domain.request.connector.BusinessMode;
import org.apache.seatunnel.app.domain.request.job.DataSourceOption;
import org.apache.seatunnel.app.domain.request.job.SelectTableFields;
import org.apache.seatunnel.app.domain.response.datasource.VirtualTableDetailRes;
import org.apache.seatunnel.app.dynamicforms.FormStructure;
import org.apache.seatunnel.app.thirdparty.datasource.AbstractDataSourceConfigSwitcher;
import org.apache.seatunnel.app.thirdparty.datasource.DataSourceConfigSwitcher;
import org.apache.seatunnel.common.constants.PluginType;
import org.apache.seatunnel.datasource.plugin.s3.S3OptionRule;

import com.google.auto.service.AutoService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@AutoService(DataSourceConfigSwitcher.class)
public class S3DataSourceConfigSwitcher extends AbstractDataSourceConfigSwitcher {

    private static String getDateFormatPattern(Object formatObj) {
        if (formatObj == null) {
            return "yyyy-MM-dd";
        }
        String format = formatObj.toString();
        try {
            return org.apache.seatunnel.common.utils.DateUtils.Formatter.valueOf(format).getValue();
        } catch (IllegalArgumentException e) {
            if ("YYYY_MM_DD".equals(format)) return "yyyy-MM-dd";
            if ("YYYY_MM_DD_SPOT".equals(format)) return "yyyy.MM.dd";
            if ("YYYY_MM_DD_SLASH".equals(format)) return "yyyy/MM/dd";
            return format;
        }
    }

    private static String getDateTimeFormatPattern(Object formatObj) {
        if (formatObj == null) {
            return "yyyy-MM-dd HH:mm:ss";
        }
        String format = formatObj.toString();
        try {
            return org.apache.seatunnel.common.utils.DateTimeUtils.Formatter.valueOf(format)
                    .getValue();
        } catch (IllegalArgumentException e) {
            if ("YYYY_MM_DD_HH_MM_SS".equals(format)) return "yyyy-MM-dd HH:mm:ss";
            if ("YYYY_MM_DD_HH_MM_SS_SSSSSS".equals(format)) return "yyyy-MM-dd HH:mm:ss.SSSSSS";
            if ("YYYY_MM_DD_HH_MM_SS_SPOT".equals(format)) return "yyyy.MM.dd HH:mm:ss";
            if ("YYYY_MM_DD_HH_MM_SS_SLASH".equals(format)) return "yyyy/MM/dd HH:mm:ss";
            return format;
        }
    }

    private static String getTimeFormatPattern(Object formatObj) {
        if (formatObj == null) {
            return "HH:mm:ss";
        }
        String format = formatObj.toString();
        try {
            return org.apache.seatunnel.common.utils.TimeUtils.Formatter.valueOf(format).getValue();
        } catch (IllegalArgumentException e) {
            if ("HH_MM_SS".equals(format)) return "HH:mm:ss";
            if ("HH_MM_SS_SSS".equals(format)) return "HH:mm:ss.SSS";
            return format;
        }
    }

    public S3DataSourceConfigSwitcher() {}

    @Override
    public String getDataSourceName() {
        return "S3";
    }

    @Override
    public FormStructure filterOptionRule(
            String connectorName,
            OptionRule dataSourceOptionRule,
            OptionRule virtualTableOptionRule,
            BusinessMode businessMode,
            PluginType pluginType,
            OptionRule connectorOptionRule,
            List<RequiredOption> addRequiredOptions,
            List<Option<?>> addOptionalOptions,
            List<String> excludedKeys) {
        if (PluginType.SOURCE.equals(pluginType)) {
            excludedKeys.add(S3OptionRule.SCHEMA.key());
            // Manually add the schema option back as an optional field so it renders
            // in the UI without the problematic conditional show rule (same as HTTP).
            addOptionalOptions.add(org.apache.seatunnel.api.options.ConnectorCommonOptions.SCHEMA);
        }

        return super.filterOptionRule(
                connectorName,
                dataSourceOptionRule,
                virtualTableOptionRule,
                businessMode,
                pluginType,
                connectorOptionRule,
                addRequiredOptions,
                addOptionalOptions,
                excludedKeys);
    }

    @Override
    public Config mergeDatasourceConfig(
            Config dataSourceInstanceConfig,
            VirtualTableDetailRes virtualTableDetail,
            DataSourceOption dataSourceOption,
            SelectTableFields selectTableFields,
            BusinessMode businessMode,
            PluginType pluginType,
            Config connectorConfig) {
        if (PluginType.SOURCE.equals(pluginType)) {
            // When virtualTableDetail is available (virtual table mode), merge schema
            // and other properties from the virtual table definition.
            // When it's null (DAG mode without database/table), skip this —
            // schema comes from user's manual input or auto-injection.
            if (virtualTableDetail != null && selectTableFields != null) {
                if (org.apache.commons.collections4.CollectionUtils.isNotEmpty(
                        selectTableFields.getTableFields())) {
                    connectorConfig =
                            connectorConfig.withValue(
                                    S3OptionRule.SCHEMA.key(),
                                    KafkaKingbaseDataSourceConfigSwitcher.SchemaGenerator
                                            .generateSchemaBySelectTableFields(
                                                    virtualTableDetail, selectTableFields)
                                            .root());
                }
                connectorConfig =
                        connectorConfig
                                .withValue(
                                        S3OptionRule.PATH.key(),
                                        ConfigValueFactory.fromAnyRef(
                                                virtualTableDetail
                                                        .getDatasourceProperties()
                                                        .get(S3OptionRule.PATH.key())))
                                .withValue(
                                        S3OptionRule.TYPE.key(),
                                        ConfigValueFactory.fromAnyRef(
                                                virtualTableDetail
                                                        .getDatasourceProperties()
                                                        .get(S3OptionRule.TYPE.key())))
                                .withValue(
                                        S3OptionRule.PARSE_PARSE_PARTITION_FROM_PATH.key(),
                                        ConfigValueFactory.fromAnyRef(
                                                virtualTableDetail
                                                        .getDatasourceProperties()
                                                        .get(
                                                                S3OptionRule
                                                                        .PARSE_PARSE_PARTITION_FROM_PATH
                                                                        .key())))
                                .withValue(
                                        "date_format",
                                        ConfigValueFactory.fromAnyRef(
                                                getDateFormatPattern(
                                                        virtualTableDetail
                                                                .getDatasourceProperties()
                                                                .get("date_format"))))
                                .withValue(
                                        "datetime_format",
                                        ConfigValueFactory.fromAnyRef(
                                                getDateTimeFormatPattern(
                                                        virtualTableDetail
                                                                .getDatasourceProperties()
                                                                .get("datetime_format"))))
                                .withValue(
                                        "time_format",
                                        ConfigValueFactory.fromAnyRef(
                                                getTimeFormatPattern(
                                                        virtualTableDetail
                                                                .getDatasourceProperties()
                                                                .get("time_format"))));
            }
        } else if (PluginType.SINK.equals(pluginType)) {
            if (virtualTableDetail.getDatasourceProperties().get(S3OptionRule.TIME_FORMAT.key())
                    == null) {
                throw new IllegalArgumentException("S3 virtual table path is null");
            }
            connectorConfig =
                    connectorConfig
                            .withValue(
                                    S3OptionRule.PATH.key(),
                                    ConfigValueFactory.fromAnyRef(
                                            virtualTableDetail
                                                    .getDatasourceProperties()
                                                    .get(S3OptionRule.PATH.key())))
                            .withValue(
                                    S3OptionRule.TYPE.key(),
                                    ConfigValueFactory.fromAnyRef(
                                            virtualTableDetail
                                                    .getDatasourceProperties()
                                                    .get(S3OptionRule.TYPE.key())))
                            .withValue(
                                    S3OptionRule.PARSE_PARSE_PARTITION_FROM_PATH.key(),
                                    ConfigValueFactory.fromAnyRef(
                                            virtualTableDetail
                                                    .getDatasourceProperties()
                                                    .get(
                                                            S3OptionRule
                                                                    .PARSE_PARSE_PARTITION_FROM_PATH
                                                                    .key())))
                            .withValue(
                                    "date_format",
                                    ConfigValueFactory.fromAnyRef(
                                            virtualTableDetail
                                                    .getDatasourceProperties()
                                                    .get("date_format")))
                            .withValue(
                                    "datetime_format",
                                    ConfigValueFactory.fromAnyRef(
                                            virtualTableDetail
                                                    .getDatasourceProperties()
                                                    .get("datetime_format")))
                            .withValue(
                                    "time_format",
                                    ConfigValueFactory.fromAnyRef(
                                            virtualTableDetail
                                                    .getDatasourceProperties()
                                                    .get("time_format")));
        }
        if (connectorConfig.hasPath("date_format")) {
            connectorConfig =
                    connectorConfig.withValue(
                            "date_format",
                            ConfigValueFactory.fromAnyRef(
                                    getDateFormatPattern(
                                            connectorConfig.getString("date_format"))));
        }
        if (connectorConfig.hasPath("datetime_format")) {
            connectorConfig =
                    connectorConfig.withValue(
                            "datetime_format",
                            ConfigValueFactory.fromAnyRef(
                                    getDateTimeFormatPattern(
                                            connectorConfig.getString("datetime_format"))));
        }
        if (connectorConfig.hasPath("time_format")) {
            connectorConfig =
                    connectorConfig.withValue(
                            "time_format",
                            ConfigValueFactory.fromAnyRef(
                                    getTimeFormatPattern(
                                            connectorConfig.getString("time_format"))));
        }
        return super.mergeDatasourceConfig(
                dataSourceInstanceConfig,
                virtualTableDetail,
                dataSourceOption,
                selectTableFields,
                businessMode,
                pluginType,
                connectorConfig);
    }
}
