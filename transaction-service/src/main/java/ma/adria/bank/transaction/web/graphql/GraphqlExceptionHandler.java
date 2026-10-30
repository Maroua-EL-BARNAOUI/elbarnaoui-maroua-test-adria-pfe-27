package ma.adria.bank.transaction.web.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import ma.adria.bank.transaction.domain.AccountServiceUnavailableException;
import ma.adria.bank.transaction.domain.BusinessRuleException;
import ma.adria.bank.transaction.domain.ResourceNotFoundException;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice(basePackageClasses = TransactionGraphqlController.class)
public class GraphqlExceptionHandler {

    @GraphQlExceptionHandler
    GraphQLError notFound(ResourceNotFoundException ex, DataFetchingEnvironment env) {
        return error(ErrorType.NOT_FOUND, ex, env);
    }

    @GraphQlExceptionHandler
    GraphQLError businessRule(BusinessRuleException ex, DataFetchingEnvironment env) {
        return error(ErrorType.BAD_REQUEST, ex, env);
    }

    @GraphQlExceptionHandler
    GraphQLError badRequest(IllegalArgumentException ex, DataFetchingEnvironment env) {
        return error(ErrorType.BAD_REQUEST, ex, env);
    }

    @GraphQlExceptionHandler
    GraphQLError unavailable(AccountServiceUnavailableException ex, DataFetchingEnvironment env) {
        return error(ErrorType.INTERNAL_ERROR, ex, env);
    }

    private static GraphQLError error(ErrorType type, Exception ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
                .errorType(type)
                .message(ex.getMessage())
                .build();
    }
}
