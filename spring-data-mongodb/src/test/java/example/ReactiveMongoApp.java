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

package example;

import static org.springframework.data.mongodb.core.query.Criteria.*;
import static org.springframework.data.mongodb.core.query.Query.*;
import static org.springframework.data.mongodb.core.query.Update.*;

import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import com.mongodb.reactivestreams.client.MongoClients;

/**
 * @author Mark Paluch
 */
public class ReactiveMongoApp {

	private static final Logger log = LoggerFactory.getLogger(ReactiveMongoApp.class);

	public static void main(String[] args) throws Exception {

		CountDownLatch latch = new CountDownLatch(1);
		ReactiveMongoTemplate mongoOps = new ReactiveMongoTemplate(MongoClients.create(), "database");

mongoOps.insert(new Person("Joe", 34)).doOnNext(person -> log.info("Insert: " + person))

		.flatMap(person -> mongoOps.findById(person.getId(), Person.class))

		.doOnNext(person -> log.info("Found: " + person))

		.zipWith(person -> mongoOps.updateFirst(query(where("name").is("Joe")), update("age", 35), Person.class))

		.flatMap(tuple -> mongoOps.remove(tuple.getT1())).flatMap(deleteResult -> mongoOps.findAll(Person.class))

		.count().doOnSuccess(count -> {
			log.info("Number of people: " + count);
			latch.countDown();
		})

		.subscribe();

latch.await();

	}

	public static class Person {

		private String id;
		private String name;
		private int age;

		public Person(String name, int age) {
			this.name = name;
			this.age = age;
		}

		public String getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public int getAge() {
			return age;
		}

		@Override
		public String toString() {
			return "Person [id=" + id + ", name=" + name + ", age=" + age + "]";
		}
	}
}
