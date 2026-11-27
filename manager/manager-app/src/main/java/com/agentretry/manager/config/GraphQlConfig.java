/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.config;

import graphql.language.StringValue;
import graphql.scalars.ExtendedScalars;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

@Configuration
public class GraphQlConfig {

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        GraphQLScalarType dateTime = GraphQLScalarType.newScalar()
                .name("DateTime")
                .description("ISO-8601 date-time")
                .coercing(new Coercing<Object, String>() {
                    @Override
                    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                        if (dataFetcherResult instanceof Instant instant) {
                            return instant.toString();
                        }
                        if (dataFetcherResult instanceof OffsetDateTime offsetDateTime) {
                            return offsetDateTime.toString();
                        }
                        throw new CoercingSerializeException(
                                "Expected Instant or OffsetDateTime but was "
                                        + dataFetcherResult.getClass().getSimpleName()
                        );
                    }

                    @Override
                    public OffsetDateTime parseValue(Object input) throws CoercingParseValueException {
                        return parseDateTime(input);
                    }

                    @Override
                    public OffsetDateTime parseLiteral(Object input) throws CoercingParseLiteralException {
                        if (input instanceof StringValue stringValue) {
                            return parseDateTime(stringValue.getValue());
                        }
                        throw new CoercingParseLiteralException("Expected StringValue");
                    }

                    private OffsetDateTime parseDateTime(Object input) {
                        if (input == null) {
                            throw new CoercingParseValueException("DateTime input was null");
                        }
                        try {
                            return OffsetDateTime.parse(input.toString());
                        } catch (DateTimeParseException ex) {
                            return Instant.parse(input.toString()).atOffset(ZoneOffset.UTC);
                        }
                    }
                })
                .build();

        return wiringBuilder -> wiringBuilder
                .scalar(dateTime)
                .scalar(ExtendedScalars.GraphQLLong);
    }
}
