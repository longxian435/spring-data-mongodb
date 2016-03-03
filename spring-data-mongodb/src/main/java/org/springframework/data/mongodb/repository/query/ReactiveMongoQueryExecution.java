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

package org.springframework.data.mongodb.repository.query;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.EntityInstantiators;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.query.ResultProcessor;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.util.ClassUtils;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * TODO: Projections, GeoNear
 */
interface ReactiveMongoQueryExecution {

	Object execute(Query query, Class<?> type, String collection);

	/**
	 * {@link ReactiveMongoQueryExecution} for collection returning queries.
	 *
	 * @author Oliver Gierke
	 */
	@RequiredArgsConstructor
	static final class CollectionExecution implements ReactiveMongoQueryExecution {

		private final @NonNull ReactiveMongoOperations operations;
		private final Pageable pageable;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.repository.query.AbstractMongoQuery.Execution#execute(org.springframework.data.mongodb.core.query.Query, java.lang.Class, java.lang.String)
		 */
		@Override
		public Object execute(Query query, Class<?> type, String collection) {
			return operations.find(query.with(pageable), type, collection);
		}
	}

	/**
	 * {@link ReactiveMongoQueryExecution} for {@link Slice} query methods.
	 *
	 * @author Oliver Gierke
	 * @author Christoph Strobl
	 * @since 1.5
	 */
	@RequiredArgsConstructor
	static final class SlicedExecution implements ReactiveMongoQueryExecution {

		private final @NonNull ReactiveMongoOperations operations;
		private final @NonNull Pageable pageable;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.repository.query.AbstractMongoQuery.Execution#execute(org.springframework.data.mongodb.core.query.Query, java.lang.Class, java.lang.String)
		 */
		@Override
		@SuppressWarnings({ "unchecked", "rawtypes" })
		public Object execute(Query query, Class<?> type, String collection) {

			int pageSize = pageable.getPageSize();

			// Apply Pageable but tweak limit to peek into next page
			Query modifiedQuery = query.with(pageable).limit(pageSize + 1);
			Flux<?> flux = operations.find(modifiedQuery, type, collection);

			// TODO: Here we don't know *yet* whether there is next
			// return new ReactiveSliceImpl<>(flux, pageable, false);
			return null;
		}
	}

	/**
	 * {@link ReactiveMongoQueryExecution} for pagination queries.
	 *
	 * @author Oliver Gierke
	 */
	@RequiredArgsConstructor
	static final class PagedExecution implements ReactiveMongoQueryExecution {

		private final @NonNull ReactiveMongoOperations operations;
		private final @NonNull Pageable pageable;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.repository.query.AbstractMongoQuery.Execution#execute(org.springframework.data.mongodb.core.query.Query, java.lang.Class, java.lang.String)
		 */
		@Override
		@SuppressWarnings({ "rawtypes", "unchecked" })
		public Object execute(Query query, Class<?> type, String collection) {

			int overallLimit = query.getLimit();
			Mono<Long> count = operations.count(query, type, collection);

			// Apply raw pagination
			query = query.with(pageable);

			// Adjust limit if page would exceed the overall limit
			if (overallLimit != 0 && pageable.getOffset() + pageable.getPageSize() > overallLimit) {
				query.limit(overallLimit - pageable.getOffset());
			}

			Flux<?> flux = operations.find(query, type, collection);
			// TODO
			return null;
		}
	}

	/**
	 * {@link ReactiveMongoQueryExecution} to return a single entity.
	 *
	 * @author Oliver Gierke
	 */
	@RequiredArgsConstructor
	static final class SingleEntityExecution implements ReactiveMongoQueryExecution {

		private final ReactiveMongoOperations operations;
		private final boolean countProjection;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.repository.query.AbstractMongoQuery.Execution#execute(org.springframework.data.mongodb.core.query.Query, java.lang.Class, java.lang.String)
		 */
		@Override
		public Object execute(Query query, Class<?> type, String collection) {
			return countProjection ? operations.count(query, type, collection) : operations.findOne(query, type, collection);
		}
	}

	/**
	 * {@link ReactiveMongoQueryExecution} removing documents matching the query.
	 *
	 * @since 1.5
	 */
	@RequiredArgsConstructor
	static final class DeleteExecution implements ReactiveMongoQueryExecution {

		private final ReactiveMongoOperations operations;
		private final MongoQueryMethod method;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.repository.query.AbstractMongoQuery.Execution#execute(org.springframework.data.mongodb.core.query.Query, java.lang.Class, java.lang.String)
		 */
		@Override
		public Object execute(Query query, Class<?> type, String collection) {

			if (method.isCollectionQuery()) {
				return operations.findAllAndRemove(query, type, collection);
			}

			return operations.remove(query, type, collection).map(deleteResult -> deleteResult.getDeletedCount());
		}
	}

	/**
	 * An {@link ReactiveMongoQueryExecution} that wraps the results of the given delegate with the given result
	 * processing.
	 *
	 * @author Oliver Gierke
	 * @since 1.9
	 */
	@RequiredArgsConstructor
	static final class ResultProcessingExecution implements ReactiveMongoQueryExecution {

		private final @NonNull ReactiveMongoQueryExecution delegate;
		private final @NonNull Converter<Object, Object> converter;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.data.mongodb.repository.query.AbstractMongoQuery.Execution#execute(org.springframework.data.mongodb.core.query.Query, java.lang.Class, java.lang.String)
		 */
		@Override
		public Object execute(Query query, Class<?> type, String collection) {
			return converter.convert(delegate.execute(query, type, collection));
		}
	}

	/**
	 * A {@link Converter} to post-process all source objects using the given {@link ResultProcessor}.
	 *
	 * @author Oliver Gierke
	 * @since 1.9
	 */
	@RequiredArgsConstructor
	static final class ResultProcessingConverter implements Converter<Object, Object> {

		private final @NonNull ResultProcessor processor;
		private final @NonNull ReactiveMongoOperations operations;
		private final @NonNull EntityInstantiators instantiators;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.core.convert.converter.Converter#convert(java.lang.Object)
		 */
		@Override
		public Object convert(Object source) {

			ReturnedType returnedType = processor.getReturnedType();

			if (ClassUtils.isPrimitiveOrWrapper(returnedType.getReturnedType())) {
				return source;
			}

			Converter<Object, Object> converter = new DtoInstantiatingConverter(returnedType.getReturnedType(),
					operations.getConverter().getMappingContext(), instantiators);

			return processor.processResult(source, converter);
		}
	}
}
