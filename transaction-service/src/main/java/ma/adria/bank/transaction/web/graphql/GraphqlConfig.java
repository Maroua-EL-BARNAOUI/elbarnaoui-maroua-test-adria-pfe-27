package ma.adria.bank.transaction.web.graphql;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.math.BigDecimal;
import java.util.Locale;

@Configuration
public class GraphqlConfig {

    /** Le scalaire BigDecimal évite toute perte de précision sur les montants. */
    @Bean
    RuntimeWiringConfigurer bigDecimalScalar() {
        return wiring -> wiring.scalar(BIG_DECIMAL);
    }

    static final GraphQLScalarType BIG_DECIMAL = GraphQLScalarType.newScalar()
            .name("BigDecimal")
            .description("Nombre décimal de précision arbitraire")
            .coercing(new Coercing<BigDecimal, BigDecimal>() {

                @Override
                public BigDecimal serialize(Object result, GraphQLContext context, Locale locale) {
                    try {
                        return toBigDecimal(result);
                    } catch (NumberFormatException e) {
                        throw new CoercingSerializeException("Valeur décimale invalide : " + result);
                    }
                }

                @Override
                public BigDecimal parseValue(Object input, GraphQLContext context, Locale locale) {
                    try {
                        return toBigDecimal(input);
                    } catch (NumberFormatException e) {
                        throw new CoercingParseValueException("Valeur décimale invalide : " + input);
                    }
                }

                @Override
                public BigDecimal parseLiteral(Value<?> input, CoercedVariables variables,
                                               GraphQLContext context, Locale locale) {
                    if (input instanceof IntValue intValue) {
                        return new BigDecimal(intValue.getValue());
                    }
                    if (input instanceof FloatValue floatValue) {
                        return floatValue.getValue();
                    }
                    if (input instanceof StringValue stringValue) {
                        try {
                            return new BigDecimal(stringValue.getValue());
                        } catch (NumberFormatException e) {
                            throw new CoercingParseLiteralException("Valeur décimale invalide : " + stringValue.getValue());
                        }
                    }
                    throw new CoercingParseLiteralException("Un nombre décimal est attendu");
                }

                private BigDecimal toBigDecimal(Object value) {
                    if (value instanceof BigDecimal decimal) {
                        return decimal;
                    }
                    if (value instanceof Number || value instanceof CharSequence) {
                        return new BigDecimal(value.toString());
                    }
                    throw new NumberFormatException(String.valueOf(value));
                }
            })
            .build();
}
