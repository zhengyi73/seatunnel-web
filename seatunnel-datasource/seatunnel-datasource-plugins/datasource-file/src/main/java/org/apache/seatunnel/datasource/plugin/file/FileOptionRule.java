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

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.Options;
import org.apache.seatunnel.api.configuration.util.OptionRule;

import java.util.Map;

public class FileOptionRule {

    public static final Option<String> PATH =
            Options.key("path").stringType().noDefaultValue().withDescription("File path");

    public static final Option<FileFormat> FILE_FORMAT_TYPE =
            Options.key("file_format_type")
                    .enumType(FileFormat.class)
                    .noDefaultValue()
                    .withDescription("File format type");

    public static final Option<String> SHEET_NAME =
            Options.key("sheet_name")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Excel sheet name");

    public static final Option<String> EXCEL_ENGINE =
            Options.key("excel_engine")
                    .stringType()
                    .defaultValue("POI")
                    .withDescription("Excel engine (POI or EasyExcel)");

    public static final Option<String> DELIMITER =
            Options.key("field_delimiter")
                    .stringType()
                    .defaultValue(",")
                    .withDescription("Field delimiter");

    public static final Option<Map<String, String>> SCHEMA =
            Options.key("schema").mapType().noDefaultValue().withDescription("Schema");

    public static OptionRule optionRule() {
        return OptionRule.builder()
                .required(PATH, FILE_FORMAT_TYPE)
                .conditional(FILE_FORMAT_TYPE, FileFormat.EXCEL, SHEET_NAME, EXCEL_ENGINE)
                .conditional(FILE_FORMAT_TYPE, FileFormat.TEXT, DELIMITER)
                .optional(SCHEMA)
                .build();
    }

    public static OptionRule metadataRule() {
        return OptionRule.builder().required(PATH, FILE_FORMAT_TYPE).build();
    }

    public enum FileFormat {
        CSV("csv"),
        TEXT("text"),
        PARQUET("parquet"),
        ORC("orc"),
        JSON("json"),
        EXCEL("excel");

        private final String type;

        FileFormat(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }
}
