package com.healthcare.interop.adapter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.healthcare.interop.common.util.JsonUtils;
import org.springframework.stereotype.Service;

import java.util.EnumSet;

@Service
public class ResponseExtractor {

    private static final Configuration JSONPATH_CONFIG = Configuration.builder()
        .options(EnumSet.of(Option.SUPPRESS_EXCEPTIONS))
        .build();

    public JsonNode extract(JsonNode response, String jsonPath) {
        if (jsonPath == null || jsonPath.isBlank()) return response;
        try {
            Object extracted = JsonPath.using(JSONPATH_CONFIG)
                .parse(response.toString())
                .read(jsonPath);
            return JsonUtils.toJsonNode(JsonUtils.toJson(extracted));
        } catch (Exception e) {
            return response;
        }
    }
}
