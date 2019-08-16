package io.wizzie.normalizer.funcs.impl;

import static com.cookingfox.guava_preconditions.Preconditions.checkNotNull;

import java.util.Base64;
import java.util.Map;

import org.apache.kafka.streams.KeyValue;

import io.wizzie.metrics.MetricsManager;
import io.wizzie.normalizer.funcs.MapperFunction;

public class Base64Mapper extends MapperFunction {

	String dimension;

	@Override
	public void prepare(Map<String, Object> properties, MetricsManager metricsManager) {
		dimension = (String) checkNotNull(properties.get("dimension"), "dimension cannot be null");
	}

	@Override
	public KeyValue<String, Map<String, Object>> process(String key, Map<String, Object> value) {

		if (value != null) {
			String string = (String) value.get(dimension);

			if (string != null)
				value.put(dimension, Base64.getEncoder().encodeToString(string.getBytes()));
		}

		return new KeyValue<>(key, value);
	}

	@Override
	public void stop() {

	}
}
