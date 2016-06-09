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

package org.springframework.data.mongodb.repository;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.data.mongodb.repository.support.SimpleReactiveMongoRepository;
import org.springframework.data.repository.query.DefaultEvaluationContextProvider;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.test.TestSubscriber;

/**
 * Test for {@link ReactiveMongoRepository}.
 *
 * @author Mark Paluch
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:reactive-infrastructure.xml")
public class SimpleReactiveMongoRepositoryTests implements BeanClassLoaderAware, BeanFactoryAware {

	@Autowired ReactiveMongoTemplate template;

	ReactiveMongoRepositoryFactory factory;
	private ClassLoader classLoader;
	private BeanFactory beanFactory;
	private ReactivePersonRepostitory repository;

	ReactivePerson dave, oliver, carter, boyd, stefan, leroi, alicia;

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader == null ? org.springframework.util.ClassUtils.getDefaultClassLoader() : classLoader;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	@Before
	public void setUp() throws Exception {

		factory = new ReactiveMongoRepositoryFactory(template);
		factory.setRepositoryBaseClass(SimpleReactiveMongoRepository.class);
		factory.setBeanClassLoader(classLoader);
		factory.setBeanFactory(beanFactory);
		factory.setEvaluationContextProvider(DefaultEvaluationContextProvider.INSTANCE);

		repository = factory.getRepository(ReactivePersonRepostitory.class);

		repository.deleteAll().block();

		dave = new ReactivePerson("Dave", "Matthews", 42);
		oliver = new ReactivePerson("Oliver August", "Matthews", 4);
		carter = new ReactivePerson("Carter", "Beauford", 49);
		boyd = new ReactivePerson("Boyd", "Tinsley", 45);
		stefan = new ReactivePerson("Stefan", "Lessard", 34);
		leroi = new ReactivePerson("Leroi", "Moore", 41);
		alicia = new ReactivePerson("Alicia", "Keys", 30);

		TestSubscriber<ReactivePerson> subscriber = TestSubscriber.create();
		repository.save(Arrays.asList(oliver, dave, carter, boyd, stefan, leroi, alicia)).subscribe(subscriber);

		subscriber.await().assertComplete().assertNoError();
	}

	@Test
	public void existsByIdShouldReturnTrueForExistingObject() throws Exception {

		Boolean exists = repository.exists(dave.id).block();

		assertThat(exists, is(true));
	}

	@Test
	public void existsByIdShouldReturnFalseForAbsentObject() throws Exception {

		TestSubscriber<Boolean> testSubscriber = TestSubscriber.subscribe(repository.exists("unknown"));

		testSubscriber.await().assertComplete().assertValues(false).assertNoError();
	}

	@Test
	public void existsByMonoOfIdShouldReturnTrueForExistingObject() throws Exception {

		Boolean exists = repository.exists(Mono.just(dave.id)).block();
		assertThat(exists, is(true));
	}

	@Test
	public void existsByEmptyMonoOfIdShouldReturnEmptyMono() throws Exception {

		TestSubscriber<Boolean> testSubscriber = TestSubscriber.subscribe(repository.exists(Mono.empty()));

		testSubscriber.await().assertComplete().assertNoValues().assertNoError();
	}

	@Test
	public void findOneShouldReturnObject() throws Exception {

		ReactivePerson person = repository.findOne(dave.id).block();

		assertThat(person.getFirstname(), is(equalTo("Dave")));
	}

	@Test
	public void findOneShouldCompleteWithoutValueForAbsentObject() throws Exception {

		TestSubscriber<ReactivePerson> testSubscriber = TestSubscriber.subscribe(repository.findOne("unknown"));

		testSubscriber.await().assertComplete().assertNoValues().assertNoError();
	}

	@Test
	public void findOneByMonoOfIdShouldReturnTrueForExistingObject() throws Exception {

		ReactivePerson person = repository.findOne(Mono.just(dave.id)).block();

		assertThat(person.id, is(equalTo(dave.id)));
	}

	@Test
	public void findOneByEmptyMonoOfIdShouldReturnEmptyMono() throws Exception {

		TestSubscriber<ReactivePerson> testSubscriber = TestSubscriber.subscribe(repository.findOne(Mono.empty()));

		testSubscriber.await().assertComplete().assertNoValues().assertNoError();
	}

	@Test
	public void findAllShouldReturnAllResults() throws Exception {

		List<ReactivePerson> persons = repository.findAll().collectList().block();

		assertThat(persons, hasSize(7));
	}

	@Test
	public void findAllByIterableOfIdShouldReturnResults() throws Exception {

		List<ReactivePerson> persons = repository.findAll(Arrays.asList(dave.id, boyd.id)).collectList().block();

		assertThat(persons, hasSize(2));
	}

	@Test
	public void findAllByPublisherOfIdShouldReturnResults() throws Exception {

		List<ReactivePerson> persons = repository.findAll(Flux.just(dave.id, boyd.id)).collectList().block();

		assertThat(persons, hasSize(2));
	}

	@Test
	public void findAllByEmptyPublisherOfIdShouldReturnResults() throws Exception {

		TestSubscriber<ReactivePerson> testSubscriber = TestSubscriber.subscribe(repository.findAll(Flux.empty()));

		testSubscriber.await().assertComplete().assertNoValues().assertNoError();
	}

	@Test
	public void findAllWithSortShouldReturnResults() throws Exception {

		List<ReactivePerson> persons = repository.findAll(new Sort(new Order(Direction.ASC, "age"))).collectList().block();

		assertThat(persons, hasSize(7));
		assertThat(persons.get(0).getId(), is(equalTo(oliver.getId())));
	}

	@Test
	@Ignore("Fix reactive page")
	public void findAllWithPageRequestShouldReturnPage() throws Exception {

		Page<ReactivePerson> people = repository.findAll(new PageRequest(1, 10)).block();

		assertThat(people.getTotalPages(), is(1));

		List<String> ids = people.getContent().stream().map(ReactivePerson::getId).collect(Collectors.toList());

		assertThat(ids, hasSize(7));
		assertThat(ids, contains(dave.id, carter.id));
	}

	@Test
	public void findAllWithPageRequestOfPageSize1ShouldReturnPage() throws Exception {

		Page<ReactivePerson> people = repository.findAll(new PageRequest(1, 1)).block();

		List<String> ids = people.getContent().stream().map(ReactivePerson::getId).collect(Collectors.toList());

		assertThat(people.getTotalPages(), is(7));
		assertThat(ids, hasSize(1));
	}

	@Test
	public void countShouldReturnNumberOfRecords() throws Exception {

		TestSubscriber<Long> testSubscriber = TestSubscriber.subscribe(repository.count());

		testSubscriber.await().assertComplete().assertValueCount(1).assertValues(7L).assertNoError();
	}

	@Test
	public void insertEntityShouldInsertEntity() throws Exception {

		repository.deleteAll().block();

		ReactivePerson person = new ReactivePerson("Homer", "Simpson", 36);

		TestSubscriber<ReactivePerson> testSubscriber = TestSubscriber.subscribe(repository.insert(person));

		testSubscriber.await().assertComplete().assertValueCount(1).assertValues(person);

		assertThat(person.getId(), is(notNullValue()));
	}

	@Test
	public void insertShouldDeferredWrite() throws Exception {

		ReactivePerson person = new ReactivePerson("Homer", "Simpson", 36);

		repository.insert(person);

		assertThat(person.getId(), is(nullValue()));
	}

	@Test
	public void insertIterableOfEntitiesShouldInsertEntity() throws Exception {

		repository.deleteAll().block();

		dave.setId(null);
		oliver.setId(null);
		boyd.setId(null);

		TestSubscriber<ReactivePerson> testSubscriber = TestSubscriber.subscribe(repository.insert(Arrays.asList(dave, oliver, boyd)));

		testSubscriber.await().assertComplete().assertValueCount(3).assertValues(dave, oliver, boyd);

		assertThat(dave.getId(), is(notNullValue()));
		assertThat(oliver.getId(), is(notNullValue()));
		assertThat(boyd.getId(), is(notNullValue()));
	}

	@Test
	public void insertPublisherOfEntitiesShouldInsertEntity() throws Exception {

		repository.deleteAll().block();

		dave.setId(null);
		oliver.setId(null);
		boyd.setId(null);

		TestSubscriber<ReactivePerson> testSubscriber = TestSubscriber.subscribe(repository.insert(Flux.just(dave, oliver, boyd)));

		testSubscriber.await().assertComplete().assertValueCount(3);

		assertThat(dave.getId(), is(notNullValue()));
		assertThat(oliver.getId(), is(notNullValue()));
		assertThat(boyd.getId(), is(notNullValue()));
	}

	@Test
	public void saveEntityShouldUpdateExistingEntity() throws Exception {

		dave.setFirstname("Hello, Dave");
		dave.setLastname("Bowman");

		TestSubscriber<ReactivePerson> testSubscriber = TestSubscriber.subscribe(repository.save(dave));

		testSubscriber.await().assertComplete().assertValueCount(1).assertValues(dave);

		List<ReactivePerson> matthews = repository.findByLastname("Matthews").collectList().block();
		assertThat(matthews, hasSize(1));
		assertThat(matthews, contains(oliver));
		assertThat(matthews, not(contains(dave)));

		ReactivePerson reactivePerson = repository.findOne(dave.id).block();

		assertThat(reactivePerson.getFirstname(), is(equalTo(dave.getFirstname())));
		assertThat(reactivePerson.getLastname(), is(equalTo(dave.getLastname())));
	}

	@Test
	public void saveEntityShouldInsertNewEntity() throws Exception {

		ReactivePerson person = new ReactivePerson("Homer", "Simpson", 36);

		TestSubscriber<ReactivePerson> testSubscriber = TestSubscriber.subscribe(repository.save(person));

		testSubscriber.await().assertComplete().assertValueCount(1).assertValues(person);

		ReactivePerson reactivePerson = repository.findOne(person.id).block();

		assertThat(reactivePerson.getFirstname(), is(equalTo(person.getFirstname())));
		assertThat(reactivePerson.getLastname(), is(equalTo(person.getLastname())));
	}

	@Test
	public void saveIterableOfNewEntitiesShouldInsertEntity() throws Exception {

		repository.deleteAll().block();

		dave.setId(null);
		oliver.setId(null);
		boyd.setId(null);

		TestSubscriber<ReactivePerson> testSubscriber = TestSubscriber.subscribe(repository.save(Arrays.asList(dave, oliver, boyd)));

		testSubscriber.await().assertComplete().assertValueCount(3).assertValues(dave, oliver, boyd);

		assertThat(dave.getId(), is(notNullValue()));
		assertThat(oliver.getId(), is(notNullValue()));
		assertThat(boyd.getId(), is(notNullValue()));
	}

	@Test
	public void saveIterableOfMixedEntitiesShouldInsertEntity() throws Exception {

		ReactivePerson person = new ReactivePerson("Homer", "Simpson", 36);

		dave.setFirstname("Hello, Dave");
		dave.setLastname("Bowman");

		TestSubscriber<ReactivePerson> testSubscriber = TestSubscriber.subscribe(repository.save(Arrays.asList(person, dave)));

		testSubscriber.await().assertComplete().assertValueCount(2);

		ReactivePerson persistentDave = repository.findOne(dave.id).block();
		assertThat(persistentDave, is(equalTo(dave)));

		assertThat(person.id, is(notNullValue()));
		ReactivePerson persistentHomer = repository.findOne(person.id).block();
		assertThat(persistentHomer, is(equalTo(person)));
	}

	@Test
	public void savePublisherOfEntitiesShouldInsertEntity() throws Exception {

		repository.deleteAll().block();

		dave.setId(null);
		oliver.setId(null);
		boyd.setId(null);

		TestSubscriber<ReactivePerson> testSubscriber = TestSubscriber.subscribe(repository.save(Flux.just(dave, oliver, boyd)));

		testSubscriber.await().assertComplete().assertValueCount(3);

		assertThat(dave.getId(), is(notNullValue()));
		assertThat(oliver.getId(), is(notNullValue()));
		assertThat(boyd.getId(), is(notNullValue()));
	}

	@Test
	public void deleteAllShouldRemoveEntities() throws Exception {

		repository.deleteAll().block();

		TestSubscriber<ReactivePerson> testSubscriber = TestSubscriber.subscribe(repository.findAll());

		testSubscriber.await().assertComplete().assertValueCount(0);
	}

	@Test
	public void deleteByIdShouldRemoveEntity() throws Exception {

		TestSubscriber<Void> testSubscriber = TestSubscriber.subscribe(repository.delete(dave.id));

		testSubscriber.await().assertComplete().assertNoValues();

		TestSubscriber<ReactivePerson> verificationSubscriber = TestSubscriber.subscribe(repository.findOne(dave.id));

		verificationSubscriber.await().assertComplete().assertNoValues();
	}

	@Test
	public void deleteShouldRemoveEntity() throws Exception {

		TestSubscriber<Void> testSubscriber = TestSubscriber.subscribe(repository.delete(dave));

		testSubscriber.await().assertComplete().assertNoValues();

		TestSubscriber<ReactivePerson> verificationSubscriber = TestSubscriber.subscribe(repository.findOne(dave.id));

		verificationSubscriber.await().assertComplete().assertNoValues();
	}

	@Test
	public void deleteIterableOfEntitiesShouldRemoveEntities() throws Exception {

		TestSubscriber<Void> testSubscriber = TestSubscriber.subscribe(repository.delete(Arrays.asList(dave, boyd)));

		testSubscriber.await().assertComplete().assertNoValues();

		TestSubscriber<ReactivePerson> verificationSubscriber = TestSubscriber.subscribe(repository.findOne(boyd.id));
		verificationSubscriber.await().assertComplete().assertNoValues();

		List<ReactivePerson> matthews = repository.findByLastname("Matthews").collectList().block();
		assertThat(matthews, hasSize(1));
		assertThat(matthews, contains(oliver));

	}

	@Test
	public void deletePublisherOfEntitiesShouldRemoveEntities() throws Exception {

		TestSubscriber<Void> testSubscriber = TestSubscriber.subscribe(repository.delete(Flux.just(dave, boyd)));

		testSubscriber.await().assertComplete().assertNoValues();

		TestSubscriber<ReactivePerson> verificationSubscriber = TestSubscriber.subscribe(repository.findOne(boyd.id));
		verificationSubscriber.await().assertComplete().assertNoValues();

		List<ReactivePerson> matthews = repository.findByLastname("Matthews").collectList().block();
		assertThat(matthews, hasSize(1));
		assertThat(matthews, contains(oliver));

	}

	static interface ReactivePersonRepostitory extends ReactiveMongoRepository<ReactivePerson, String> {

		Flux<ReactivePerson> findByLastname(String lastname);

	}

	@Data
	@NoArgsConstructor
	static class ReactivePerson {

		@Id String id;

		String firstname;
		String lastname;
		int age;

		public ReactivePerson(String firstname, String lastname, int age) {

			this.firstname = firstname;
			this.lastname = lastname;
			this.age = age;
		}
	}
}
