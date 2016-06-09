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

import static com.sun.prism.impl.Disposer.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

import java.util.List;

import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.mongodb.MongoException;
import com.mongodb.ReadPreference;
import com.mongodb.reactivestreams.client.MongoDatabase;

import reactor.core.publisher.Flux;
import reactor.core.test.TestSubscriber;

/**
 * Integration test for {@link ReactiveMongoTemplate} execute methods.
 *
 * @author Mark Paluch
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:reactive-infrastructure.xml")
public class ReactiveMongoTemplateExecuteTests {

	private static final org.springframework.data.util.Version THREE = org.springframework.data.util.Version.parse("3.0");

	@Autowired ReactiveMongoDbFactory factory;
	@Autowired ReactiveMongoOperations operations;

	@Rule public ExpectedException thrown = ExpectedException.none();

	org.springframework.data.util.Version mongoVersion;

	@Before
	public void setUp() {
		cleanUp();

		if (mongoVersion == null) {
			org.bson.Document result = operations.executeCommand("{ buildInfo: 1 }").next().block();
			mongoVersion = org.springframework.data.util.Version.parse(result.get("version").toString());
		}
	}

	@After
	public void tearDown() {

		operations.dropCollection("person").block();
		operations.dropCollection(Person.class).block();
		operations.dropCollection("execute_test").block();
		operations.dropCollection("execute_test1").block();
		operations.dropCollection("execute_test2").block();
		operations.dropCollection("execute_index_test").block();
	}

	@Test
	public void executeCommandJsonCommandShouldReturnSingleResponse() throws Exception {

		Document document = operations.executeCommand("{ buildInfo: 1 }").next().block();

		assertThat(document, hasKey("version"));
	}

	@Test
	public void executeCommandDocumentCommandShouldReturnSingleResponse() throws Exception {

		Document document = operations.executeCommand(new Document("buildInfo", 1)).next().block();

		assertThat(document, hasKey("version"));
	}

	@Test
	public void executeCommandJsonCommandShouldReturnMultipleResponses() throws Exception {

		assumeTrue(mongoVersion.isGreaterThan(THREE));

		operations.executeCommand("{ insert: 'execute_test', documents: [{},{},{}]}").next().block();

		TestSubscriber<Document> subscriber = TestSubscriber.create();
		operations.executeCommand("{ find: 'execute_test'}").subscribe(subscriber);

		subscriber.awaitAndAssertNextValueCount(1);
		subscriber.assertValuesWith(document -> {

			assertThat(document, hasKey("waitedMS"));
			assertThat(document, hasKey("cursor"));
		});
	}

	@Test
	public void executeCommandJsonCommandShouldTranslateExceptions() throws Exception {

		TestSubscriber<Document> testSubscriber = TestSubscriber.subscribe(operations.executeCommand("{ unknown: 1 }"));

		testSubscriber.await().assertError(InvalidDataAccessApiUsageException.class);
	}

	@Test
	public void executeCommandDocumentCommandShouldTranslateExceptions() throws Exception {

		TestSubscriber<Document> testSubscriber = TestSubscriber
				.subscribe(operations.executeCommand(new Document("unknown", 1)));

		testSubscriber.await().assertError(InvalidDataAccessApiUsageException.class);
	}

	@Test
	public void executeCommandWithReadPreferenceCommandShouldTranslateExceptions() throws Exception {

		TestSubscriber<Document> testSubscriber = TestSubscriber
				.subscribe(operations.executeCommand(new Document("unknown", 1), ReadPreference.nearest()));

		testSubscriber.await().assertError(InvalidDataAccessApiUsageException.class);
	}

	@Test
	public void executeOnDatabaseShouldExecuteCommand() throws Exception {

		operations.executeCommand("{ insert: 'execute_test', documents: [{},{},{}]}").next().block();
		operations.executeCommand("{ insert: 'execute_test1', documents: [{},{},{}]}").next().block();
		operations.executeCommand("{ insert: 'execute_test2', documents: [{},{},{}]}").next().block();

		Flux<Document> execute = operations.execute(MongoDatabase::listCollections);

		List<Document> documents = execute.filter(document -> document.getString("name").startsWith("execute_test"))
				.collectList().block();

		assertThat(documents, hasSize(3));
	}

	@Test
	public void executeOnDatabaseShouldDeferExecution() throws Exception {

		operations.execute(db -> {
			throw new MongoException(50, "hi there");
		});

		// the assertion here is that the exception is not thrown
	}

	@Test
	public void executeOnDatabaseShouldShouldTranslateExceptions() throws Exception {

		TestSubscriber<Document> testSubscriber = TestSubscriber.create();

		Flux<Document> execute = operations.execute(db -> {
			throw new MongoException(50, "hi there");
		});

		execute.subscribe(testSubscriber);

		testSubscriber.await().assertError(UncategorizedMongoDbException.class);
	}

	@Test
	public void executeOnCollectionWithTypeShouldReturnFindResults() throws Exception {

		operations.executeCommand("{ insert: 'person', documents: [{},{},{}]}").next().block();

		TestSubscriber<Document> testSubscriber = TestSubscriber.create();

		Flux<Document> execute = operations.execute(Person.class, collection -> collection.find());
		execute.subscribe(testSubscriber);

		testSubscriber.awaitAndAssertNextValueCount(3).assertComplete();
	}

	@Test
	public void executeOnCollectionWithNameShouldReturnFindResults() throws Exception {

		operations.executeCommand("{ insert: 'execute_test', documents: [{},{},{}]}").next().block();

		TestSubscriber<Document> testSubscriber = TestSubscriber.create();

		Flux<Document> execute = operations.execute("execute_test", collection -> collection.find());
		execute.subscribe(testSubscriber);

		testSubscriber.awaitAndAssertNextValueCount(3).assertComplete();
	}
}
