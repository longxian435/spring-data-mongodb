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

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactivestreams.Publisher;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.data.mongodb.repository.support.SimpleReactiveMongoRepository;
import org.springframework.data.repository.query.DefaultEvaluationContextProvider;
import org.springframework.data.repository.reactive.ReactivePagingAndSortingRepository;
import org.springframework.data.repository.reactive.RxJavaPagingAndSortingRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.test.TestSubscriber;
import rx.Observable;
import rx.Single;

/**
 * Test for {@link ReactiveMongoRepository} using reative wrapper type conversion.
 *
 * @author Mark Paluch
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:reactive-infrastructure.xml")
public class ConvertingReactiveMongoRepositoryTests implements BeanClassLoaderAware, BeanFactoryAware {

	@Autowired ReactiveMongoTemplate template;

	ReactiveMongoRepositoryFactory factory;
	private ClassLoader classLoader;
	private BeanFactory beanFactory;
	private MixedReactivePersonRepostitory reactiveRepository;
	private ReactivePersonRepostitory reactivePersonRepostitory;
	private RxJavaPersonRepostitory rxJavaPersonRepostitory;

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

		reactiveRepository = factory.getRepository(MixedReactivePersonRepostitory.class);
		reactivePersonRepostitory = factory.getRepository(ReactivePersonRepostitory.class);
		rxJavaPersonRepostitory = factory.getRepository(RxJavaPersonRepostitory.class);

		reactiveRepository.deleteAll().block();

		dave = new ReactivePerson("Dave", "Matthews", 42);
		oliver = new ReactivePerson("Oliver August", "Matthews", 4);
		carter = new ReactivePerson("Carter", "Beauford", 49);
		boyd = new ReactivePerson("Boyd", "Tinsley", 45);
		stefan = new ReactivePerson("Stefan", "Lessard", 34);
		leroi = new ReactivePerson("Leroi", "Moore", 41);
		alicia = new ReactivePerson("Alicia", "Keys", 30);

		TestSubscriber<ReactivePerson> subscriber = TestSubscriber.create();
		reactiveRepository.save(Arrays.asList(oliver, dave, carter, boyd, stefan, leroi, alicia)).subscribe(subscriber);

		subscriber.await().assertComplete().assertNoError();
	}

	/**
	 * @see DATAMONGO-1444
	 */
	@Test
	public void reactiveStreamsMethodsShouldWork() throws Exception {

		TestSubscriber<Boolean> subscriber = TestSubscriber.subscribe(reactivePersonRepostitory.exists(dave.getId()));

		subscriber.awaitAndAssertNextValueCount(1).assertValues(true);
	}

	/**
	 * @see DATAMONGO-1444
	 */
	@Test
	public void reactiveStreamsQueryMethodsShouldWork() throws Exception {

		TestSubscriber<ReactivePerson> subscriber = TestSubscriber
				.subscribe(reactivePersonRepostitory.findByLastname(boyd.getLastname()));

		subscriber.awaitAndAssertNextValueCount(1).assertValues(boyd);
	}

	/**
	 * @see DATAMONGO-1444
	 */
	@Test
	public void simpleRxJavaMethodsShouldWork() throws Exception {

		rx.observers.TestSubscriber<Boolean> subscriber = new rx.observers.TestSubscriber<>();
		rxJavaPersonRepostitory.exists(dave.getId()).subscribe(subscriber);

		subscriber.awaitTerminalEvent();
		subscriber.assertCompleted();
		subscriber.assertNoErrors();
		subscriber.assertValue(true);
	}

	/**
	 * @see DATAMONGO-1444
	 */
	@Test
	public void existsWithSingleRxJavaIdMethodsShouldWork() throws Exception {

		rx.observers.TestSubscriber<Boolean> subscriber = new rx.observers.TestSubscriber<>();
		rxJavaPersonRepostitory.exists(Single.just(dave.getId())).subscribe(subscriber);

		subscriber.awaitTerminalEvent();
		subscriber.assertCompleted();
		subscriber.assertNoErrors();
		subscriber.assertValue(true);
	}

	/**
	 * @see DATAMONGO-1444
	 */
	@Test
	public void singleRxJavaQueryMethodShouldWork() throws Exception {

		rx.observers.TestSubscriber<ReactivePerson> subscriber = new rx.observers.TestSubscriber<>();
		rxJavaPersonRepostitory.findByFirstnameAndLastname(dave.getFirstname(), dave.getLastname()).subscribe(subscriber);

		subscriber.awaitTerminalEvent();
		subscriber.assertCompleted();
		subscriber.assertNoErrors();
		subscriber.assertValue(dave);
	}

	/**
	 * @see DATAMONGO-1444
	 */
	@Test
	public void singleProjectedRxJavaQueryMethodShouldWork() throws Exception {

		rx.observers.TestSubscriber<ProjectedPerson> subscriber = new rx.observers.TestSubscriber<>();
		rxJavaPersonRepostitory.findProjectedByLastname(carter.getLastname()).subscribe(subscriber);

		subscriber.awaitTerminalEvent();
		subscriber.assertCompleted();
		subscriber.assertNoErrors();

		ProjectedPerson projectedPerson = subscriber.getOnNextEvents().get(0);
		assertThat(projectedPerson.getFirstname(), is(equalTo(carter.getFirstname())));
	}

	/**
	 * @see DATAMONGO-1444
	 */
	@Test
	public void observableRxJavaQueryMethodShouldWork() throws Exception {

		rx.observers.TestSubscriber<ReactivePerson> subscriber = new rx.observers.TestSubscriber<>();
		rxJavaPersonRepostitory.findByLastname(boyd.getLastname()).subscribe(subscriber);

		subscriber.awaitTerminalEvent();
		subscriber.assertCompleted();
		subscriber.assertNoErrors();
		subscriber.assertValue(boyd);
	}

	/**
	 * @see DATAMONGO-1444
	 */
	@Test
	public void mixedRepositoryShouldWork() throws Exception {

		ReactivePerson value = reactiveRepository.findByLastname(boyd.getLastname()).toBlocking().value();

		assertThat(value, is(equalTo(boyd)));
	}

	static interface ReactivePersonRepostitory extends ReactivePagingAndSortingRepository<ReactivePerson, String> {

		Publisher<ReactivePerson> findByLastname(String lastname);
	}

	static interface RxJavaPersonRepostitory extends RxJavaPagingAndSortingRepository<ReactivePerson, String> {

		Observable<ReactivePerson> findByFirstnameAndLastname(String firstname, String lastname);

		Single<ReactivePerson> findByLastname(String lastname);

		Single<ProjectedPerson> findProjectedByLastname(String lastname);
	}

	static interface MixedReactivePersonRepostitory extends ReactiveMongoRepository<ReactivePerson, String> {

		Single<ReactivePerson> findByLastname(String lastname);
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

	interface ProjectedPerson {

		public String getId();

		public String getFirstname();
	}
}
