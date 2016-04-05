import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

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

public class SomeWiredMongo3StuffTests {

	MongoClient client;
	MongoDatabase database;
	MongoCollection<DBObject> collection;

	@Before
	public void setUp() {
		client = new MongoClient();
		database = client.getDatabase("_mongo3tests_");
		database.createCollection("client-tests");
		collection = database.getCollection("client-tests", DBObject.class);
	}

	@After
	public void tearDown() {
		database.drop();
		client.close();
	}

	@Test
	public void shouldSaveUpdate() {

		BasicDBObject dbo = new BasicDBObject("_id", "1");
		dbo.put("_class", "foo");
		dbo.put("name", "bar");

		collection.insertOne(dbo);

		BasicDBObject filter = new BasicDBObject("_id", "1");

		System.out.println(collection.find(filter));

		BasicDBObject update = new BasicDBObject("_id", "1");
		update.put("_class", "foo");
		update.put("name", "foo-bar");

		collection.replaceOne(filter, update);

		System.out.println(collection.find(filter));

	}
}
