package com.example.smoketests;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * Smoke tests to bootstrap Spring module components to verify they are functional.
 */
class SmokeTestsApplicationTests {

	@Test
	void cassandra() {

		CassandraMappingContext context = new CassandraMappingContext();
		assertThat(context).isNotNull();
	}

	@Test
	void elasticsearch() {

		SimpleElasticsearchMappingContext context = new SimpleElasticsearchMappingContext();
		assertThat(context).isNotNull();
	}

	@Test
	void mongo() {

		MongoMappingContext context = new MongoMappingContext();
		assertThat(context).isNotNull();
	}

	@Test
	void jpa() {
		assertThat(JpaSort.unsafe("hello")).isNotNull();
	}

	@Test
	void jdbc() {
		JdbcMappingContext context = new JdbcMappingContext();
		assertThat(context).isNotNull();
	}

}
