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
package org.springframework.data.mongodb.core;

import java.net.UnknownHostException;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.data.authentication.UserCredentials;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.mongodb.ConnectionString;
import com.mongodb.WriteConcern;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * Factory to create {@link MongoDatabase} instances from a {@link MongoClient} instance.
 *
 * @author Mark Paluch
 */
public class ReactiveMongoDbFactory implements DisposableBean {

	private final MongoClient mongo;
	private final String databaseName;
	private final boolean mongoInstanceCreated;
	private final UserCredentials credentials;
	private final PersistenceExceptionTranslator exceptionTranslator;
	private final String authenticationDatabaseName;

	private WriteConcern writeConcern;

	/**
	 * Creates a new {@link ReactiveMongoDbFactory} instance from the given {@link ConnectionString}.
	 *
	 * @param connectionString must not be {@literal null}.
	 * @throws UnknownHostException
	 * @since 1.7
	 */
	public ReactiveMongoDbFactory(ConnectionString connectionString) throws UnknownHostException {
		this(MongoClients.create(), connectionString.getDatabase(), true);
	}

	/**
	 * Creates a new {@link ReactiveMongoDbFactory} instance from the given {@link MongoClient}.
	 *
	 * @param mongoClient must not be {@literal null}.
	 * @param databaseName must not be {@literal null}.
	 * @since 1.7
	 */
	public ReactiveMongoDbFactory(MongoClient mongoClient, String databaseName) {
		this(mongoClient, databaseName, false);
	}

	private ReactiveMongoDbFactory(MongoClient mongoClient, String databaseName, UserCredentials credentials,
								   boolean mongoInstanceCreated, String authenticationDatabaseName) {

		Assert.notNull(mongoClient, "Mongo must not be null");
		Assert.hasText(databaseName, "Database name must not be empty");
		Assert.isTrue(databaseName.matches("[\\w-]+"),
				"Database name must only contain letters, numbers, underscores and dashes!");

		this.mongo = mongoClient;
		this.databaseName = databaseName;
		this.mongoInstanceCreated = mongoInstanceCreated;
		this.credentials = credentials == null ? UserCredentials.NO_CREDENTIALS : credentials;
		this.exceptionTranslator = new MongoExceptionTranslator();
		this.authenticationDatabaseName = StringUtils.hasText(authenticationDatabaseName) ? authenticationDatabaseName
				: databaseName;

		Assert.isTrue(this.authenticationDatabaseName.matches("[\\w-]+"),
				"Authentication database name must only contain letters, numbers, underscores and dashes!");
	}

	/**
	 * @param client
	 * @param databaseName
	 * @param mongoInstanceCreated
	 * @since 1.7
	 */
	private ReactiveMongoDbFactory(MongoClient client, String databaseName, boolean mongoInstanceCreated) {

		Assert.notNull(client, "MongoClient must not be null!");
		Assert.hasText(databaseName, "Database name must not be empty!");

		this.mongo = client;
		this.databaseName = databaseName;
		this.mongoInstanceCreated = mongoInstanceCreated;
		this.exceptionTranslator = new MongoExceptionTranslator();
		this.credentials = UserCredentials.NO_CREDENTIALS;
		this.authenticationDatabaseName = databaseName;
	}

	/**
	 * Configures the {@link WriteConcern} to be used on the {@link MongoDatabase} instance being created.
	 *
	 * @param writeConcern the writeConcern to set
	 */
	public void setWriteConcern(WriteConcern writeConcern) {
		this.writeConcern = writeConcern;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.ReactiveMongoDbFactory#getMongoDatabase()
	 */
	public MongoDatabase getMongoDatabase() throws DataAccessException {
		return getMongoDatabase(databaseName);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.ReactiveMongoDbFactory#getMongoDatabase(java.lang.String)
	 */
	public MongoDatabase getMongoDatabase(String dbName) throws DataAccessException {

		Assert.hasText(dbName, "Database name must not be empty.");

		MongoDatabase db = ReactiveMongoDbUtils.getMongoDatabase(mongo, dbName);

		if (writeConcern != null) {

			db = db.withWriteConcern(writeConcern);
		}

		return db;
	}

	/**
	 * Clean up the Mongo instance if it was created by the factory itself.
	 *
	 * @see DisposableBean#destroy()
	 */
	public void destroy() throws Exception {
		if (mongoInstanceCreated) {
			mongo.close();
		}
	}

	private static String parseChars(char[] chars) {
		return chars == null ? null : String.valueOf(chars);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mongodb.ReactiveMongoDbFactory#getExceptionTranslator()
	 */
	public PersistenceExceptionTranslator getExceptionTranslator() {
		return this.exceptionTranslator;
	}
}
