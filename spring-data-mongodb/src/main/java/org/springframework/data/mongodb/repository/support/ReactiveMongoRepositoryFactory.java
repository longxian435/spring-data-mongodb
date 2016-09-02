/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.mongodb.repository.support;

import java.io.Serializable;
import java.lang.reflect.Method;

import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.model.MappingException;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.mongodb.repository.query.MongoEntityInformation;
import org.springframework.data.mongodb.repository.query.MongoQueryMethod;
import org.springframework.data.mongodb.repository.query.PartTreeMongoQuery;
import org.springframework.data.mongodb.repository.query.ReactivePartTreeMongoQuery;
import org.springframework.data.mongodb.repository.query.ReactiveStringBasedMongoQuery;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.query.EvaluationContextProvider;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryLookupStrategy.Key;
import org.springframework.data.repository.query.ReactiveWrappers;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.util.QueryExecutionConverters;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.Assert;

/**
 * Factory to create {@link org.springframework.data.mongodb.repository.ReactiveMongoRepository} instances.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class ReactiveMongoRepositoryFactory extends RepositoryFactorySupport {

	private static final SpelExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

	private final ReactiveMongoOperations operations;
	private final MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext;
	private final ConversionService conversionService;

	/**
	 * Creates a new {@link ReactiveMongoRepositoryFactory} with the given {@link ReactiveMongoOperations}.
	 *
	 * @param mongoOperations must not be {@literal null}.
	 */
	public ReactiveMongoRepositoryFactory(ReactiveMongoOperations mongoOperations) {

		Assert.notNull(mongoOperations);

		this.operations = mongoOperations;
		this.mappingContext = mongoOperations.getConverter().getMappingContext();

		DefaultConversionService conversionService = new DefaultConversionService();
		QueryExecutionConverters.registerConvertersIn(conversionService);
		this.conversionService = conversionService;
		setConversionService(conversionService);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.core.support.RepositoryFactorySupport#getRepositoryBaseClass(org.springframework.data.repository.core.RepositoryMetadata)
	 */
	@Override
	protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
		return SimpleReactiveMongoRepository.class;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.core.support.RepositoryFactorySupport#getTargetRepository(org.springframework.data.repository.core.RepositoryInformation)
	 */
	@Override
	protected Object getTargetRepository(RepositoryInformation information) {

		MongoEntityInformation<?, Serializable> entityInformation = getEntityInformation(information.getDomainType(),
				information);
		return getTargetRepositoryViaReflection(information, entityInformation, operations);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.core.support.RepositoryFactorySupport#getQueryLookupStrategy(org.springframework.data.repository.query.QueryLookupStrategy.Key, org.springframework.data.repository.query.EvaluationContextProvider)
	 */
	@Override
	protected QueryLookupStrategy getQueryLookupStrategy(Key key, EvaluationContextProvider evaluationContextProvider) {
		return new MongoQueryLookupStrategy(operations, evaluationContextProvider, mappingContext, conversionService);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.core.support.RepositoryFactorySupport#getEntityInformation(java.lang.Class)
	 */
	public <T, ID extends Serializable> MongoEntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {
		return getEntityInformation(domainClass, null);
	}

	@SuppressWarnings("unchecked")
	private <T, ID extends Serializable> MongoEntityInformation<T, ID> getEntityInformation(Class<T> domainClass,
			RepositoryInformation information) {

		MongoPersistentEntity<?> entity = mappingContext.getPersistentEntity(domainClass);

		if (entity == null) {
			throw new MappingException(
					String.format("Could not lookup mapping metadata for domain class %s!", domainClass.getName()));
		}

		return new MappingMongoEntityInformation<T, ID>((MongoPersistentEntity<T>) entity,
				information != null ? (Class<ID>) information.getIdType() : null);
	}

	/**
	 * {@link QueryLookupStrategy} to create {@link PartTreeMongoQuery} instances.
	 *
	 * @author Oliver Gierke
	 * @author Thomas Darimont
	 */
	private static class MongoQueryLookupStrategy implements QueryLookupStrategy {

		private final ReactiveMongoOperations operations;
		private final EvaluationContextProvider evaluationContextProvider;
		MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext;
		final ConversionService conversionService;

		MongoQueryLookupStrategy(ReactiveMongoOperations operations, EvaluationContextProvider evaluationContextProvider,
				MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext,
				ConversionService conversionService) {

			this.operations = operations;
			this.evaluationContextProvider = evaluationContextProvider;
			this.mappingContext = mappingContext;
			this.conversionService = conversionService;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.repository.query.QueryLookupStrategy#resolveQuery(java.lang.reflect.Method, org.springframework.data.repository.core.RepositoryMetadata, org.springframework.data.projection.ProjectionFactory, org.springframework.data.repository.core.NamedQueries)
		 */
		@Override
		public RepositoryQuery resolveQuery(Method method, RepositoryMetadata metadata, ProjectionFactory factory,
				NamedQueries namedQueries) {

			MongoQueryMethod queryMethod = new ReactiveMongoQueryMethod(method, metadata, factory, mappingContext);
			String namedQueryName = queryMethod.getNamedQueryName();

			if (namedQueries.hasQuery(namedQueryName)) {
				String namedQuery = namedQueries.getQuery(namedQueryName);
				return new ReactiveStringBasedMongoQuery(namedQuery, queryMethod, operations, EXPRESSION_PARSER,
						evaluationContextProvider, conversionService);
			} else if (queryMethod.hasAnnotatedQuery()) {
				return new ReactiveStringBasedMongoQuery(queryMethod, operations, EXPRESSION_PARSER, evaluationContextProvider,
						conversionService);
			} else {
				return new ReactivePartTreeMongoQuery(queryMethod, operations, conversionService);
			}
		}
	}

	private static class ReactiveMongoQueryMethod extends MongoQueryMethod {

		private Method method;

		ReactiveMongoQueryMethod(Method method, RepositoryMetadata metadata, ProjectionFactory projectionFactory,
				MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext) {
			super(method, metadata, projectionFactory, mappingContext);

			this.method = method;
		}

		@Override
		public boolean isCollectionQuery() {
			return !(isPageQuery() || isSliceQuery()) && ReactiveWrappers.isMultiType(method.getReturnType());
		}

		@Override
		public boolean isModifyingQuery() {
			return super.isModifyingQuery();
		}

		@Override
		public boolean isQueryForEntity() {
			return super.isQueryForEntity();
		}

		@Override
		public boolean isStreamQuery() {
			return false;
		}
	}
}
