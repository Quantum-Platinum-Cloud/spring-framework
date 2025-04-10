/*
 * Copyright 2002-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.annotation;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.testfixture.beans.ITestBean;
import org.springframework.beans.testfixture.beans.IndexedTestBean;
import org.springframework.beans.testfixture.beans.NestedTestBean;
import org.springframework.beans.testfixture.beans.TestBean;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.core.testfixture.io.SerializationTestUtils;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Sam Brannen
 * @author Chris Beams
 * @author Stephane Nicoll
 */
public class AutowiredAnnotationBeanPostProcessorTests {

	private DefaultListableBeanFactory bf = new DefaultListableBeanFactory();

	private AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();


	@BeforeEach
	void setup() {
		bf.registerResolvableDependency(BeanFactory.class, bf);
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver());
		bf.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
	}

	@AfterEach
	void close() {
		bf.destroySingletons();
	}


	@Test
	void incompleteBeanDefinition() {
		bf.registerBeanDefinition("testBean", new GenericBeanDefinition());
		assertThatExceptionOfType(BeanCreationException.class).isThrownBy(() ->
				bf.getBean("testBean"))
			.withRootCauseInstanceOf(IllegalStateException.class);
	}

	@Test
	void resourceInjection() {
		RootBeanDefinition bd = new RootBeanDefinition(ResourceInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		ResourceInjectionBean bean = (ResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);

		bean = (ResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
	}

	@Test
	void resourceInjectionWithNullBean() {
		RootBeanDefinition bd = new RootBeanDefinition(NonPublicResourceInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		RootBeanDefinition tb = new RootBeanDefinition(NullFactoryMethods.class);
		tb.setFactoryMethodName("createTestBean");
		bf.registerBeanDefinition("testBean", tb);

		NonPublicResourceInjectionBean bean = (NonPublicResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isNull();
		assertThat(bean.getTestBean2()).isNull();
		assertThat(bean.getTestBean3()).isNull();

		bean = (NonPublicResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isNull();
		assertThat(bean.getTestBean2()).isNull();
		assertThat(bean.getTestBean3()).isNull();
	}

	@Test
	void extendedResourceInjection() {
		RootBeanDefinition bd = new RootBeanDefinition(TypedExtendedResourceInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		NestedTestBean ntb = new NestedTestBean();
		bf.registerSingleton("nestedTestBean", ntb);

		TypedExtendedResourceInjectionBean bean = (TypedExtendedResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBean()).isSameAs(ntb);
		assertThat(bean.getBeanFactory()).isSameAs(bf);

		bean = (TypedExtendedResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBean()).isSameAs(ntb);
		assertThat(bean.getBeanFactory()).isSameAs(bf);

		String[] depBeans = bf.getDependenciesForBean("annotatedBean");
		assertThat(depBeans).hasSize(2);
		assertThat(depBeans[0]).isEqualTo("testBean");
		assertThat(depBeans[1]).isEqualTo("nestedTestBean");
	}

	@Test
	void extendedResourceInjectionWithDestruction() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(TypedExtendedResourceInjectionBean.class));
		bf.registerBeanDefinition("testBean", new RootBeanDefinition(TestBean.class));
		NestedTestBean ntb = new NestedTestBean();
		bf.registerSingleton("nestedTestBean", ntb);

		TestBean tb = bf.getBean("testBean", TestBean.class);
		TypedExtendedResourceInjectionBean bean = (TypedExtendedResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBean()).isSameAs(ntb);
		assertThat(bean.getBeanFactory()).isSameAs(bf);

		assertThat(bf.getDependenciesForBean("annotatedBean")).isEqualTo(new String[] {"testBean", "nestedTestBean"});
		bf.destroySingleton("testBean");
		assertThat(bf.containsSingleton("testBean")).isFalse();
		assertThat(bf.containsSingleton("annotatedBean")).isFalse();
		assertThat(bean.destroyed).isTrue();
		assertThat(bf.getDependenciesForBean("annotatedBean").length).isSameAs(0);
	}

	@Test
	void extendedResourceInjectionWithOverriding() {
		RootBeanDefinition annotatedBd = new RootBeanDefinition(TypedExtendedResourceInjectionBean.class);
		TestBean tb2 = new TestBean();
		annotatedBd.getPropertyValues().add("testBean2", tb2);
		bf.registerBeanDefinition("annotatedBean", annotatedBd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		NestedTestBean ntb = new NestedTestBean();
		bf.registerSingleton("nestedTestBean", ntb);

		TypedExtendedResourceInjectionBean bean = (TypedExtendedResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb2);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBean()).isSameAs(ntb);
		assertThat(bean.getBeanFactory()).isSameAs(bf);
	}

	@Test
	void extendedResourceInjectionWithSkippedOverriddenMethods() {
		RootBeanDefinition annotatedBd = new RootBeanDefinition(OverriddenExtendedResourceInjectionBean.class);
		bf.registerBeanDefinition("annotatedBean", annotatedBd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		NestedTestBean ntb = new NestedTestBean();
		bf.registerSingleton("nestedTestBean", ntb);

		OverriddenExtendedResourceInjectionBean bean = (OverriddenExtendedResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isNull();
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBean()).isSameAs(ntb);
		assertThat(bean.getBeanFactory()).isNull();
		assertThat(bean.baseInjected).isTrue();
		assertThat(bean.subInjected).isTrue();
	}

	@Test
	void extendedResourceInjectionWithDefaultMethod() {
		RootBeanDefinition annotatedBd = new RootBeanDefinition(DefaultMethodResourceInjectionBean.class);
		bf.registerBeanDefinition("annotatedBean", annotatedBd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		NestedTestBean ntb = new NestedTestBean();
		bf.registerSingleton("nestedTestBean", ntb);

		DefaultMethodResourceInjectionBean bean = (DefaultMethodResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isNull();
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBean()).isSameAs(ntb);
		assertThat(bean.getBeanFactory()).isNull();
		assertThat(bean.baseInjected).isTrue();
		assertThat(bean.subInjected).isTrue();
	}

	@Test
	void optionalResourceInjection() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(OptionalResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		IndexedTestBean itb = new IndexedTestBean();
		bf.registerSingleton("indexedTestBean", itb);
		NestedTestBean ntb1 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		NestedTestBean ntb2 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		OptionalResourceInjectionBean bean = (OptionalResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getIndexedTestBean()).isSameAs(itb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans()[0]).isSameAs(ntb1);
		assertThat(bean.getNestedTestBeans()[1]).isSameAs(ntb2);
		assertThat(bean.nestedTestBeansField).hasSize(2);
		assertThat(bean.nestedTestBeansField[0]).isSameAs(ntb1);
		assertThat(bean.nestedTestBeansField[1]).isSameAs(ntb2);
	}

	@Test
	void optionalResourceInjectionWithSingletonRemoval() {
		RootBeanDefinition rbd = new RootBeanDefinition(OptionalResourceInjectionBean.class);
		rbd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", rbd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		IndexedTestBean itb = new IndexedTestBean();
		bf.registerSingleton("indexedTestBean", itb);
		NestedTestBean ntb1 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		NestedTestBean ntb2 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		OptionalResourceInjectionBean bean = (OptionalResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getIndexedTestBean()).isSameAs(itb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans()[0]).isSameAs(ntb1);
		assertThat(bean.getNestedTestBeans()[1]).isSameAs(ntb2);
		assertThat(bean.nestedTestBeansField).hasSize(2);
		assertThat(bean.nestedTestBeansField[0]).isSameAs(ntb1);
		assertThat(bean.nestedTestBeansField[1]).isSameAs(ntb2);

		bf.destroySingleton("testBean");

		bean = (OptionalResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isNull();
		assertThat(bean.getTestBean2()).isNull();
		assertThat(bean.getTestBean3()).isNull();
		assertThat(bean.getTestBean4()).isNull();
		assertThat(bean.getIndexedTestBean()).isSameAs(itb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans()[0]).isSameAs(ntb1);
		assertThat(bean.getNestedTestBeans()[1]).isSameAs(ntb2);
		assertThat(bean.nestedTestBeansField).hasSize(2);
		assertThat(bean.nestedTestBeansField[0]).isSameAs(ntb1);
		assertThat(bean.nestedTestBeansField[1]).isSameAs(ntb2);

		bf.registerSingleton("testBean", tb);

		bean = (OptionalResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getIndexedTestBean()).isSameAs(itb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans()[0]).isSameAs(ntb1);
		assertThat(bean.getNestedTestBeans()[1]).isSameAs(ntb2);
		assertThat(bean.nestedTestBeansField).hasSize(2);
		assertThat(bean.nestedTestBeansField[0]).isSameAs(ntb1);
		assertThat(bean.nestedTestBeansField[1]).isSameAs(ntb2);
	}

	@Test
	void optionalResourceInjectionWithBeanDefinitionRemoval() {
		RootBeanDefinition rbd = new RootBeanDefinition(OptionalResourceInjectionBean.class);
		rbd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", rbd);
		bf.registerBeanDefinition("testBean", new RootBeanDefinition(TestBean.class));
		IndexedTestBean itb = new IndexedTestBean();
		bf.registerSingleton("indexedTestBean", itb);
		NestedTestBean ntb1 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		NestedTestBean ntb2 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		OptionalResourceInjectionBean bean = (OptionalResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(bf.getBean("testBean"));
		assertThat(bean.getTestBean2()).isSameAs(bf.getBean("testBean"));
		assertThat(bean.getTestBean3()).isSameAs(bf.getBean("testBean"));
		assertThat(bean.getTestBean4()).isSameAs(bf.getBean("testBean"));
		assertThat(bean.getIndexedTestBean()).isSameAs(itb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans()[0]).isSameAs(ntb1);
		assertThat(bean.getNestedTestBeans()[1]).isSameAs(ntb2);
		assertThat(bean.nestedTestBeansField).hasSize(2);
		assertThat(bean.nestedTestBeansField[0]).isSameAs(ntb1);
		assertThat(bean.nestedTestBeansField[1]).isSameAs(ntb2);

		bf.removeBeanDefinition("testBean");

		bean = (OptionalResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isNull();
		assertThat(bean.getTestBean2()).isNull();
		assertThat(bean.getTestBean3()).isNull();
		assertThat(bean.getTestBean4()).isNull();
		assertThat(bean.getIndexedTestBean()).isSameAs(itb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans()[0]).isSameAs(ntb1);
		assertThat(bean.getNestedTestBeans()[1]).isSameAs(ntb2);
		assertThat(bean.nestedTestBeansField).hasSize(2);
		assertThat(bean.nestedTestBeansField[0]).isSameAs(ntb1);
		assertThat(bean.nestedTestBeansField[1]).isSameAs(ntb2);

		bf.registerBeanDefinition("testBean", new RootBeanDefinition(TestBean.class));

		bean = (OptionalResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(bf.getBean("testBean"));
		assertThat(bean.getTestBean2()).isSameAs(bf.getBean("testBean"));
		assertThat(bean.getTestBean3()).isSameAs(bf.getBean("testBean"));
		assertThat(bean.getTestBean4()).isSameAs(bf.getBean("testBean"));
		assertThat(bean.getIndexedTestBean()).isSameAs(itb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans()[0]).isSameAs(ntb1);
		assertThat(bean.getNestedTestBeans()[1]).isSameAs(ntb2);
		assertThat(bean.nestedTestBeansField).hasSize(2);
		assertThat(bean.nestedTestBeansField[0]).isSameAs(ntb1);
		assertThat(bean.nestedTestBeansField[1]).isSameAs(ntb2);
	}

	@Test
	void optionalCollectionResourceInjection() {
		RootBeanDefinition rbd = new RootBeanDefinition(OptionalCollectionResourceInjectionBean.class);
		rbd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", rbd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		IndexedTestBean itb = new IndexedTestBean();
		bf.registerSingleton("indexedTestBean", itb);
		NestedTestBean ntb1 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		NestedTestBean ntb2 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		// Two calls to verify that caching doesn't break re-creation.
		OptionalCollectionResourceInjectionBean bean = (OptionalCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		bean = (OptionalCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getIndexedTestBean()).isSameAs(itb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans().get(0)).isSameAs(ntb1);
		assertThat(bean.getNestedTestBeans().get(1)).isSameAs(ntb2);
		assertThat(bean.nestedTestBeansSetter).hasSize(2);
		assertThat(bean.nestedTestBeansSetter.get(0)).isSameAs(ntb1);
		assertThat(bean.nestedTestBeansSetter.get(1)).isSameAs(ntb2);
		assertThat(bean.nestedTestBeansField).hasSize(2);
		assertThat(bean.nestedTestBeansField.get(0)).isSameAs(ntb1);
		assertThat(bean.nestedTestBeansField.get(1)).isSameAs(ntb2);
	}

	@Test
	void optionalCollectionResourceInjectionWithSingleElement() {
		RootBeanDefinition rbd = new RootBeanDefinition(OptionalCollectionResourceInjectionBean.class);
		rbd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", rbd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		IndexedTestBean itb = new IndexedTestBean();
		bf.registerSingleton("indexedTestBean", itb);
		NestedTestBean ntb1 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);

		// Two calls to verify that caching doesn't break re-creation.
		OptionalCollectionResourceInjectionBean bean = (OptionalCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		bean = (OptionalCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getIndexedTestBean()).isSameAs(itb);
		assertThat(bean.getNestedTestBeans()).hasSize(1);
		assertThat(bean.getNestedTestBeans().get(0)).isSameAs(ntb1);
		assertThat(bean.nestedTestBeansSetter).hasSize(1);
		assertThat(bean.nestedTestBeansSetter.get(0)).isSameAs(ntb1);
		assertThat(bean.nestedTestBeansField).hasSize(1);
		assertThat(bean.nestedTestBeansField.get(0)).isSameAs(ntb1);
	}

	@Test
	void optionalResourceInjectionWithIncompleteDependencies() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(OptionalResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		OptionalResourceInjectionBean bean = (OptionalResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isNull();
		assertThat(bean.getNestedTestBeans()).isNull();
	}

	@Test
	void optionalResourceInjectionWithNoDependencies() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(OptionalResourceInjectionBean.class));

		OptionalResourceInjectionBean bean = (OptionalResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isNull();
		assertThat(bean.getTestBean2()).isNull();
		assertThat(bean.getTestBean3()).isNull();
		assertThat(bean.getTestBean4()).isNull();
		assertThat(bean.getNestedTestBeans()).isNull();
	}

	@Test
	void orderedResourceInjection() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(OptionalResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		IndexedTestBean itb = new IndexedTestBean();
		bf.registerSingleton("indexedTestBean", itb);
		OrderedNestedTestBean ntb1 = new OrderedNestedTestBean();
		ntb1.setOrder(2);
		bf.registerSingleton("nestedTestBean1", ntb1);
		OrderedNestedTestBean ntb2 = new OrderedNestedTestBean();
		ntb2.setOrder(1);
		bf.registerSingleton("nestedTestBean2", ntb2);

		OptionalResourceInjectionBean bean = (OptionalResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getIndexedTestBean()).isSameAs(itb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans()[0]).isSameAs(ntb2);
		assertThat(bean.getNestedTestBeans()[1]).isSameAs(ntb1);
		assertThat(bean.nestedTestBeansField).hasSize(2);
		assertThat(bean.nestedTestBeansField[0]).isSameAs(ntb2);
		assertThat(bean.nestedTestBeansField[1]).isSameAs(ntb1);
	}

	@Test
	void annotationOrderedResourceInjection() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(OptionalResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		IndexedTestBean itb = new IndexedTestBean();
		bf.registerSingleton("indexedTestBean", itb);
		FixedOrder2NestedTestBean ntb1 = new FixedOrder2NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		FixedOrder1NestedTestBean ntb2 = new FixedOrder1NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		OptionalResourceInjectionBean bean = (OptionalResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getIndexedTestBean()).isSameAs(itb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans()[0]).isSameAs(ntb2);
		assertThat(bean.getNestedTestBeans()[1]).isSameAs(ntb1);
		assertThat(bean.nestedTestBeansField).hasSize(2);
		assertThat(bean.nestedTestBeansField[0]).isSameAs(ntb2);
		assertThat(bean.nestedTestBeansField[1]).isSameAs(ntb1);
	}

	@Test
	void orderedCollectionResourceInjection() {
		RootBeanDefinition rbd = new RootBeanDefinition(OptionalCollectionResourceInjectionBean.class);
		rbd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", rbd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		IndexedTestBean itb = new IndexedTestBean();
		bf.registerSingleton("indexedTestBean", itb);
		OrderedNestedTestBean ntb1 = new OrderedNestedTestBean();
		ntb1.setOrder(2);
		bf.registerSingleton("nestedTestBean1", ntb1);
		OrderedNestedTestBean ntb2 = new OrderedNestedTestBean();
		ntb2.setOrder(1);
		bf.registerSingleton("nestedTestBean2", ntb2);

		// Two calls to verify that caching doesn't break re-creation.
		OptionalCollectionResourceInjectionBean bean = (OptionalCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		bean = (OptionalCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getIndexedTestBean()).isSameAs(itb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans().get(0)).isSameAs(ntb2);
		assertThat(bean.getNestedTestBeans().get(1)).isSameAs(ntb1);
		assertThat(bean.nestedTestBeansSetter).hasSize(2);
		assertThat(bean.nestedTestBeansSetter.get(0)).isSameAs(ntb2);
		assertThat(bean.nestedTestBeansSetter.get(1)).isSameAs(ntb1);
		assertThat(bean.nestedTestBeansField).hasSize(2);
		assertThat(bean.nestedTestBeansField.get(0)).isSameAs(ntb2);
		assertThat(bean.nestedTestBeansField.get(1)).isSameAs(ntb1);
	}

	@Test
	void annotationOrderedCollectionResourceInjection() {
		RootBeanDefinition rbd = new RootBeanDefinition(OptionalCollectionResourceInjectionBean.class);
		rbd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", rbd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		IndexedTestBean itb = new IndexedTestBean();
		bf.registerSingleton("indexedTestBean", itb);
		FixedOrder2NestedTestBean ntb1 = new FixedOrder2NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		FixedOrder1NestedTestBean ntb2 = new FixedOrder1NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		// Two calls to verify that caching doesn't break re-creation.
		OptionalCollectionResourceInjectionBean bean = (OptionalCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		bean = (OptionalCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getIndexedTestBean()).isSameAs(itb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans().get(0)).isSameAs(ntb2);
		assertThat(bean.getNestedTestBeans().get(1)).isSameAs(ntb1);
		assertThat(bean.nestedTestBeansSetter).hasSize(2);
		assertThat(bean.nestedTestBeansSetter.get(0)).isSameAs(ntb2);
		assertThat(bean.nestedTestBeansSetter.get(1)).isSameAs(ntb1);
		assertThat(bean.nestedTestBeansField).hasSize(2);
		assertThat(bean.nestedTestBeansField.get(0)).isSameAs(ntb2);
		assertThat(bean.nestedTestBeansField.get(1)).isSameAs(ntb1);
	}

	@Test
	void constructorResourceInjection() {
		RootBeanDefinition bd = new RootBeanDefinition(ConstructorResourceInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		NestedTestBean ntb = new NestedTestBean();
		bf.registerSingleton("nestedTestBean", ntb);

		ConstructorResourceInjectionBean bean = (ConstructorResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBean()).isSameAs(ntb);
		assertThat(bean.getBeanFactory()).isSameAs(bf);

		bean = (ConstructorResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBean()).isSameAs(ntb);
		assertThat(bean.getBeanFactory()).isSameAs(bf);
	}

	@Test
	void constructorResourceInjectionWithSingletonRemoval() {
		RootBeanDefinition bd = new RootBeanDefinition(ConstructorResourceInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		NestedTestBean ntb = new NestedTestBean();
		bf.registerSingleton("nestedTestBean", ntb);

		ConstructorResourceInjectionBean bean = (ConstructorResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBean()).isSameAs(ntb);
		assertThat(bean.getBeanFactory()).isSameAs(bf);

		bf.destroySingleton("nestedTestBean");

		bean = (ConstructorResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBean()).isNull();
		assertThat(bean.getBeanFactory()).isSameAs(bf);

		bf.registerSingleton("nestedTestBean", ntb);

		bean = (ConstructorResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBean()).isSameAs(ntb);
		assertThat(bean.getBeanFactory()).isSameAs(bf);
	}

	@Test
	void constructorResourceInjectionWithBeanDefinitionRemoval() {
		RootBeanDefinition bd = new RootBeanDefinition(ConstructorResourceInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		bf.registerBeanDefinition("nestedTestBean", new RootBeanDefinition(NestedTestBean.class));

		ConstructorResourceInjectionBean bean = (ConstructorResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBean()).isSameAs(bf.getBean("nestedTestBean"));
		assertThat(bean.getBeanFactory()).isSameAs(bf);

		bf.removeBeanDefinition("nestedTestBean");

		bean = (ConstructorResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBean()).isNull();
		assertThat(bean.getBeanFactory()).isSameAs(bf);

		bf.registerBeanDefinition("nestedTestBean", new RootBeanDefinition(NestedTestBean.class));

		bean = (ConstructorResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBean()).isSameAs(bf.getBean("nestedTestBean"));
		assertThat(bean.getBeanFactory()).isSameAs(bf);
	}

	@Test
	void constructorResourceInjectionWithNullFromFactoryBean() {
		RootBeanDefinition bd = new RootBeanDefinition(ConstructorResourceInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		bf.registerBeanDefinition("nestedTestBean", new RootBeanDefinition(NullNestedTestBeanFactoryBean.class));
		bf.registerSingleton("nestedTestBean2", new NestedTestBean());

		ConstructorResourceInjectionBean bean = (ConstructorResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBean()).isNull();
		assertThat(bean.getBeanFactory()).isSameAs(bf);

		bean = (ConstructorResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getTestBean2()).isSameAs(tb);
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBean()).isNull();
		assertThat(bean.getBeanFactory()).isSameAs(bf);
	}

	@Test
	void constructorResourceInjectionWithNullFromFactoryMethod() {
		RootBeanDefinition bd = new RootBeanDefinition(ConstructorResourceInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		RootBeanDefinition tb = new RootBeanDefinition(NullFactoryMethods.class);
		tb.setFactoryMethodName("createTestBean");
		bf.registerBeanDefinition("testBean", tb);
		RootBeanDefinition ntb = new RootBeanDefinition(NullFactoryMethods.class);
		ntb.setFactoryMethodName("createNestedTestBean");
		bf.registerBeanDefinition("nestedTestBean", ntb);
		bf.registerSingleton("nestedTestBean2", new NestedTestBean());

		ConstructorResourceInjectionBean bean = (ConstructorResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isNull();
		assertThat(bean.getTestBean2()).isNull();
		assertThat(bean.getTestBean3()).isNull();
		assertThat(bean.getTestBean4()).isNull();
		assertThat(bean.getNestedTestBean()).isNull();
		assertThat(bean.getBeanFactory()).isSameAs(bf);

		bean = (ConstructorResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isNull();
		assertThat(bean.getTestBean2()).isNull();
		assertThat(bean.getTestBean3()).isNull();
		assertThat(bean.getTestBean4()).isNull();
		assertThat(bean.getNestedTestBean()).isNull();
		assertThat(bean.getBeanFactory()).isSameAs(bf);
	}

	@Test
	void constructorResourceInjectionWithMultipleCandidates() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ConstructorsResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		NestedTestBean ntb1 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		NestedTestBean ntb2 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		ConstructorsResourceInjectionBean bean = (ConstructorsResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean3()).isNull();
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans()[0]).isSameAs(ntb1);
		assertThat(bean.getNestedTestBeans()[1]).isSameAs(ntb2);
	}

	@Test
	void constructorResourceInjectionWithNoCandidatesAndNoFallback() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ConstructorWithoutFallbackBean.class));
		assertThatExceptionOfType(UnsatisfiedDependencyException.class).isThrownBy(() ->
				bf.getBean("annotatedBean"))
			.satisfies(methodParameterDeclaredOn(ConstructorWithoutFallbackBean.class));
	}

	@Test
	void constructorResourceInjectionWithCollectionAndNullFromFactoryBean() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(
				ConstructorsCollectionResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		bf.registerBeanDefinition("nestedTestBean1", new RootBeanDefinition(NullNestedTestBeanFactoryBean.class));
		NestedTestBean ntb2 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		ConstructorsCollectionResourceInjectionBean bean = (ConstructorsCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean3()).isNull();
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBeans()).hasSize(1);
		assertThat(bean.getNestedTestBeans().get(0)).isSameAs(ntb2);

		Map<String, NestedTestBean> map = bf.getBeansOfType(NestedTestBean.class);
		assertThat(map.get("nestedTestBean1")).isNull();
		assertThat(map.get("nestedTestBean2")).isSameAs(ntb2);
	}

	@Test
	void constructorResourceInjectionWithMultipleCandidatesAsCollection() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(
				ConstructorsCollectionResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		NestedTestBean ntb1 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		NestedTestBean ntb2 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		ConstructorsCollectionResourceInjectionBean bean = (ConstructorsCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean3()).isNull();
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans().get(0)).isSameAs(ntb1);
		assertThat(bean.getNestedTestBeans().get(1)).isSameAs(ntb2);
	}

	@Test
	void constructorResourceInjectionWithMultipleOrderedCandidates() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ConstructorsResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		FixedOrder2NestedTestBean ntb1 = new FixedOrder2NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		FixedOrder1NestedTestBean ntb2 = new FixedOrder1NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		ConstructorsResourceInjectionBean bean = (ConstructorsResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean3()).isNull();
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans()[0]).isSameAs(ntb2);
		assertThat(bean.getNestedTestBeans()[1]).isSameAs(ntb1);
	}

	@Test
	void constructorResourceInjectionWithMultipleCandidatesAsOrderedCollection() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ConstructorsCollectionResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		FixedOrder2NestedTestBean ntb1 = new FixedOrder2NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		FixedOrder1NestedTestBean ntb2 = new FixedOrder1NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		ConstructorsCollectionResourceInjectionBean bean = (ConstructorsCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean3()).isNull();
		assertThat(bean.getTestBean4()).isSameAs(tb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans().get(0)).isSameAs(ntb2);
		assertThat(bean.getNestedTestBeans().get(1)).isSameAs(ntb1);
	}

	@Test
	void singleConstructorInjectionWithMultipleCandidatesAsRequiredVararg() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(SingleConstructorVarargBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		FixedOrder2NestedTestBean ntb1 = new FixedOrder2NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		FixedOrder1NestedTestBean ntb2 = new FixedOrder1NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		SingleConstructorVarargBean bean = (SingleConstructorVarargBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans().get(0)).isSameAs(ntb2);
		assertThat(bean.getNestedTestBeans().get(1)).isSameAs(ntb1);
	}

	@Test
	void singleConstructorInjectionWithEmptyVararg() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(SingleConstructorVarargBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		SingleConstructorVarargBean bean = (SingleConstructorVarargBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getNestedTestBeans()).isNotNull();
		assertThat(bean.getNestedTestBeans().isEmpty()).isTrue();
	}

	@Test
	void singleConstructorInjectionWithMultipleCandidatesAsRequiredCollection() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(SingleConstructorRequiredCollectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		FixedOrder2NestedTestBean ntb1 = new FixedOrder2NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		FixedOrder1NestedTestBean ntb2 = new FixedOrder1NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		SingleConstructorRequiredCollectionBean bean = (SingleConstructorRequiredCollectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans().get(0)).isSameAs(ntb2);
		assertThat(bean.getNestedTestBeans().get(1)).isSameAs(ntb1);
	}

	@Test
	void singleConstructorInjectionWithEmptyCollection() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(SingleConstructorRequiredCollectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		SingleConstructorRequiredCollectionBean bean = (SingleConstructorRequiredCollectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getNestedTestBeans()).isNotNull();
		assertThat(bean.getNestedTestBeans().isEmpty()).isTrue();
	}

	@Test
	void singleConstructorInjectionWithMultipleCandidatesAsOrderedCollection() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(SingleConstructorOptionalCollectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		FixedOrder2NestedTestBean ntb1 = new FixedOrder2NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		FixedOrder1NestedTestBean ntb2 = new FixedOrder1NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		SingleConstructorOptionalCollectionBean bean = (SingleConstructorOptionalCollectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getNestedTestBeans()).hasSize(2);
		assertThat(bean.getNestedTestBeans().get(0)).isSameAs(ntb2);
		assertThat(bean.getNestedTestBeans().get(1)).isSameAs(ntb1);
	}

	@Test
	void singleConstructorInjectionWithEmptyCollectionAsNull() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(SingleConstructorOptionalCollectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		SingleConstructorOptionalCollectionBean bean = (SingleConstructorOptionalCollectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
		assertThat(bean.getNestedTestBeans()).isNull();
	}

	@Test
	void singleConstructorInjectionWithMissingDependency() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(SingleConstructorOptionalCollectionBean.class));
		assertThatExceptionOfType(UnsatisfiedDependencyException.class).isThrownBy(() ->
				bf.getBean("annotatedBean"));
	}

	@Test
	void singleConstructorInjectionWithNullDependency() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(SingleConstructorOptionalCollectionBean.class));
		RootBeanDefinition tb = new RootBeanDefinition(NullFactoryMethods.class);
		tb.setFactoryMethodName("createTestBean");
		bf.registerBeanDefinition("testBean", tb);
		assertThatExceptionOfType(UnsatisfiedDependencyException.class).isThrownBy(() ->
				bf.getBean("annotatedBean"));
	}

	@Test
	void constructorResourceInjectionWithMultipleCandidatesAndFallback() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ConstructorsResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		ConstructorsResourceInjectionBean bean = (ConstructorsResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean4()).isNull();
	}

	@Test
	void constructorResourceInjectionWithMultipleCandidatesAndDefaultFallback() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ConstructorsResourceInjectionBean.class));

		ConstructorsResourceInjectionBean bean = (ConstructorsResourceInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean3()).isNull();
		assertThat(bean.getTestBean4()).isNull();
	}

	@Test
	void constructorInjectionWithMap() {
		RootBeanDefinition bd = new RootBeanDefinition(MapConstructorInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		TestBean tb1 = new TestBean("tb1");
		bf.registerSingleton("testBean1", tb1);
		RootBeanDefinition tb2 = new RootBeanDefinition(NullFactoryMethods.class);
		tb2.setFactoryMethodName("createTestBean");
		bf.registerBeanDefinition("testBean2", tb2);

		MapConstructorInjectionBean bean = (MapConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanMap()).hasSize(1);
		assertThat(bean.getTestBeanMap().get("testBean1")).isSameAs(tb1);
		assertThat(bean.getTestBeanMap().get("testBean2")).isNull();

		bean = (MapConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanMap()).hasSize(1);
		assertThat(bean.getTestBeanMap().get("testBean1")).isSameAs(tb1);
		assertThat(bean.getTestBeanMap().get("testBean2")).isNull();
	}

	@Test
	void fieldInjectionWithMap() {
		RootBeanDefinition bd = new RootBeanDefinition(MapFieldInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		TestBean tb1 = new TestBean("tb1");
		TestBean tb2 = new TestBean("tb2");
		bf.registerSingleton("testBean1", tb1);
		bf.registerSingleton("testBean2", tb2);

		MapFieldInjectionBean bean = (MapFieldInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanMap()).hasSize(2);
		assertThat(bean.getTestBeanMap().keySet().contains("testBean1")).isTrue();
		assertThat(bean.getTestBeanMap().keySet().contains("testBean2")).isTrue();
		assertThat(bean.getTestBeanMap().values().contains(tb1)).isTrue();
		assertThat(bean.getTestBeanMap().values().contains(tb2)).isTrue();

		bean = (MapFieldInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanMap()).hasSize(2);
		assertThat(bean.getTestBeanMap().keySet().contains("testBean1")).isTrue();
		assertThat(bean.getTestBeanMap().keySet().contains("testBean2")).isTrue();
		assertThat(bean.getTestBeanMap().values().contains(tb1)).isTrue();
		assertThat(bean.getTestBeanMap().values().contains(tb2)).isTrue();
	}

	@Test
	void methodInjectionWithMap() {
		RootBeanDefinition bd = new RootBeanDefinition(MapMethodInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		MapMethodInjectionBean bean = (MapMethodInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanMap()).hasSize(1);
		assertThat(bean.getTestBeanMap().keySet().contains("testBean")).isTrue();
		assertThat(bean.getTestBeanMap().values().contains(tb)).isTrue();
		assertThat(bean.getTestBean()).isSameAs(tb);

		bean = (MapMethodInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanMap()).hasSize(1);
		assertThat(bean.getTestBeanMap().keySet().contains("testBean")).isTrue();
		assertThat(bean.getTestBeanMap().values().contains(tb)).isTrue();
		assertThat(bean.getTestBean()).isSameAs(tb);
	}

	@Test
	void methodInjectionWithMapAndMultipleMatches() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(MapMethodInjectionBean.class));
		bf.registerBeanDefinition("testBean1", new RootBeanDefinition(TestBean.class));
		bf.registerBeanDefinition("testBean2", new RootBeanDefinition(TestBean.class));
		assertThatExceptionOfType(UnsatisfiedDependencyException.class).as("should have failed, more than one bean of type").isThrownBy(() ->
				bf.getBean("annotatedBean"))
			.satisfies(methodParameterDeclaredOn(MapMethodInjectionBean.class));
	}

	@Test
	void methodInjectionWithMapAndMultipleMatchesButOnlyOneAutowireCandidate() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(MapMethodInjectionBean.class));
		bf.registerBeanDefinition("testBean1", new RootBeanDefinition(TestBean.class));
		RootBeanDefinition rbd2 = new RootBeanDefinition(TestBean.class);
		rbd2.setAutowireCandidate(false);
		bf.registerBeanDefinition("testBean2", rbd2);

		MapMethodInjectionBean bean = (MapMethodInjectionBean) bf.getBean("annotatedBean");
		TestBean tb = (TestBean) bf.getBean("testBean1");
		assertThat(bean.getTestBeanMap()).hasSize(1);
		assertThat(bean.getTestBeanMap().keySet().contains("testBean1")).isTrue();
		assertThat(bean.getTestBeanMap().values().contains(tb)).isTrue();
		assertThat(bean.getTestBean()).isSameAs(tb);
	}

	@Test
	void methodInjectionWithMapAndNoMatches() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(MapMethodInjectionBean.class));

		MapMethodInjectionBean bean = (MapMethodInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanMap()).isNull();
		assertThat(bean.getTestBean()).isNull();
	}

	@Test
	void constructorInjectionWithTypedMapAsBean() {
		RootBeanDefinition bd = new RootBeanDefinition(MapConstructorInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		MyTestBeanMap tbm = new MyTestBeanMap();
		tbm.put("testBean1", new TestBean("tb1"));
		tbm.put("testBean2", new TestBean("tb2"));
		bf.registerSingleton("testBeans", tbm);
		bf.registerSingleton("otherMap", new Properties());

		MapConstructorInjectionBean bean = (MapConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanMap()).isSameAs(tbm);
		bean = (MapConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanMap()).isSameAs(tbm);
	}

	@Test
	void constructorInjectionWithPlainMapAsBean() {
		RootBeanDefinition bd = new RootBeanDefinition(MapConstructorInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		RootBeanDefinition tbm = new RootBeanDefinition(CollectionFactoryMethods.class);
		tbm.setUniqueFactoryMethodName("testBeanMap");
		bf.registerBeanDefinition("myTestBeanMap", tbm);
		bf.registerSingleton("otherMap", new HashMap<>());

		MapConstructorInjectionBean bean = (MapConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanMap()).isSameAs(bf.getBean("myTestBeanMap"));
		bean = (MapConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanMap()).isSameAs(bf.getBean("myTestBeanMap"));
	}

	@Test
	void constructorInjectionWithCustomMapAsBean() {
		RootBeanDefinition bd = new RootBeanDefinition(CustomMapConstructorInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		RootBeanDefinition tbm = new RootBeanDefinition(CustomCollectionFactoryMethods.class);
		tbm.setUniqueFactoryMethodName("testBeanMap");
		bf.registerBeanDefinition("myTestBeanMap", tbm);
		bf.registerSingleton("testBean1", new TestBean());
		bf.registerSingleton("testBean2", new TestBean());

		CustomMapConstructorInjectionBean bean = (CustomMapConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanMap()).isSameAs(bf.getBean("myTestBeanMap"));
		bean = (CustomMapConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanMap()).isSameAs(bf.getBean("myTestBeanMap"));
	}

	@Test
	void constructorInjectionWithPlainHashMapAsBean() {
		RootBeanDefinition bd = new RootBeanDefinition(QualifiedMapConstructorInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		bf.registerBeanDefinition("myTestBeanMap", new RootBeanDefinition(HashMap.class));

		QualifiedMapConstructorInjectionBean bean = (QualifiedMapConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanMap()).isSameAs(bf.getBean("myTestBeanMap"));
		bean = (QualifiedMapConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanMap()).isSameAs(bf.getBean("myTestBeanMap"));
	}

	@Test
	void constructorInjectionWithTypedSetAsBean() {
		RootBeanDefinition bd = new RootBeanDefinition(SetConstructorInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		MyTestBeanSet tbs = new MyTestBeanSet();
		tbs.add(new TestBean("tb1"));
		tbs.add(new TestBean("tb2"));
		bf.registerSingleton("testBeans", tbs);
		bf.registerSingleton("otherSet", new HashSet<>());

		SetConstructorInjectionBean bean = (SetConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanSet()).isSameAs(tbs);
		bean = (SetConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanSet()).isSameAs(tbs);
	}

	@Test
	void constructorInjectionWithPlainSetAsBean() {
		RootBeanDefinition bd = new RootBeanDefinition(SetConstructorInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		RootBeanDefinition tbs = new RootBeanDefinition(CollectionFactoryMethods.class);
		tbs.setUniqueFactoryMethodName("testBeanSet");
		bf.registerBeanDefinition("myTestBeanSet", tbs);
		bf.registerSingleton("otherSet", new HashSet<>());

		SetConstructorInjectionBean bean = (SetConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanSet()).isSameAs(bf.getBean("myTestBeanSet"));
		bean = (SetConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanSet()).isSameAs(bf.getBean("myTestBeanSet"));
	}

	@Test
	void constructorInjectionWithCustomSetAsBean() {
		RootBeanDefinition bd = new RootBeanDefinition(CustomSetConstructorInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		RootBeanDefinition tbs = new RootBeanDefinition(CustomCollectionFactoryMethods.class);
		tbs.setUniqueFactoryMethodName("testBeanSet");
		bf.registerBeanDefinition("myTestBeanSet", tbs);

		CustomSetConstructorInjectionBean bean = (CustomSetConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanSet()).isSameAs(bf.getBean("myTestBeanSet"));
		bean = (CustomSetConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBeanSet()).isSameAs(bf.getBean("myTestBeanSet"));
	}

	@Test
	void selfReference() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(SelfInjectionBean.class));

		SelfInjectionBean bean = (SelfInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.reference).isSameAs(bean);
		assertThat(bean.referenceCollection).isNull();
	}

	@Test
	void selfReferenceWithOther() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(SelfInjectionBean.class));
		bf.registerBeanDefinition("annotatedBean2", new RootBeanDefinition(SelfInjectionBean.class));

		SelfInjectionBean bean = (SelfInjectionBean) bf.getBean("annotatedBean");
		SelfInjectionBean bean2 = (SelfInjectionBean) bf.getBean("annotatedBean2");
		assertThat(bean.reference).isSameAs(bean2);
		assertThat(bean.referenceCollection).hasSize(1);
		assertThat(bean.referenceCollection.get(0)).isSameAs(bean2);
	}

	@Test
	void selfReferenceCollection() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(SelfInjectionCollectionBean.class));

		SelfInjectionCollectionBean bean = (SelfInjectionCollectionBean) bf.getBean("annotatedBean");
		assertThat(bean.reference).isSameAs(bean);
		assertThat(bean.referenceCollection).isNull();
	}

	@Test
	void selfReferenceCollectionWithOther() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(SelfInjectionCollectionBean.class));
		bf.registerBeanDefinition("annotatedBean2", new RootBeanDefinition(SelfInjectionCollectionBean.class));

		SelfInjectionCollectionBean bean = (SelfInjectionCollectionBean) bf.getBean("annotatedBean");
		SelfInjectionCollectionBean bean2 = (SelfInjectionCollectionBean) bf.getBean("annotatedBean2");
		assertThat(bean.reference).isSameAs(bean2);
		assertThat(bean2.referenceCollection).hasSize(1);
		assertThat(bean.referenceCollection.get(0)).isSameAs(bean2);
	}

	@Test
	void objectFactoryFieldInjection() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ObjectFactoryFieldInjectionBean.class));
		bf.registerBeanDefinition("testBean", new RootBeanDefinition(TestBean.class));

		ObjectFactoryFieldInjectionBean bean = (ObjectFactoryFieldInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(bf.getBean("testBean"));
	}

	@Test
	void objectFactoryConstructorInjection() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ObjectFactoryConstructorInjectionBean.class));
		bf.registerBeanDefinition("testBean", new RootBeanDefinition(TestBean.class));

		ObjectFactoryConstructorInjectionBean bean = (ObjectFactoryConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(bf.getBean("testBean"));
	}

	@Test
	void objectFactoryInjectionIntoPrototypeBean() {
		RootBeanDefinition annotatedBeanDefinition = new RootBeanDefinition(ObjectFactoryFieldInjectionBean.class);
		annotatedBeanDefinition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", annotatedBeanDefinition);
		bf.registerBeanDefinition("testBean", new RootBeanDefinition(TestBean.class));

		ObjectFactoryFieldInjectionBean bean = (ObjectFactoryFieldInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(bf.getBean("testBean"));
		ObjectFactoryFieldInjectionBean anotherBean = (ObjectFactoryFieldInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean).isNotSameAs(anotherBean);
		assertThat(anotherBean.getTestBean()).isSameAs(bf.getBean("testBean"));
	}

	@Test
	void objectFactoryQualifierInjection() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ObjectFactoryQualifierInjectionBean.class));
		RootBeanDefinition bd = new RootBeanDefinition(TestBean.class);
		bd.addQualifier(new AutowireCandidateQualifier(Qualifier.class, "testBean"));
		bf.registerBeanDefinition("dependencyBean", bd);
		bf.registerBeanDefinition("dependencyBean2", new RootBeanDefinition(TestBean.class));

		ObjectFactoryQualifierInjectionBean bean = (ObjectFactoryQualifierInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(bf.getBean("dependencyBean"));
	}

	@Test
	void objectFactoryQualifierProviderInjection() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ObjectFactoryQualifierInjectionBean.class));
		RootBeanDefinition bd = new RootBeanDefinition(TestBean.class);
		bd.setQualifiedElement(ReflectionUtils.findMethod(getClass(), "testBeanQualifierProvider"));
		bf.registerBeanDefinition("dependencyBean", bd);
		bf.registerBeanDefinition("dependencyBean2", new RootBeanDefinition(TestBean.class));

		ObjectFactoryQualifierInjectionBean bean = (ObjectFactoryQualifierInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(bf.getBean("dependencyBean"));
	}

	@Test
	void objectFactorySerialization() throws Exception {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ObjectFactoryFieldInjectionBean.class));
		bf.registerBeanDefinition("testBean", new RootBeanDefinition(TestBean.class));
		bf.setSerializationId("test");

		ObjectFactoryFieldInjectionBean bean = (ObjectFactoryFieldInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(bf.getBean("testBean"));
		bean = SerializationTestUtils.serializeAndDeserialize(bean);
		assertThat(bean.getTestBean()).isSameAs(bf.getBean("testBean"));
	}

	@Test
	void objectProviderInjectionWithPrototype() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ObjectProviderInjectionBean.class));
		RootBeanDefinition tbd = new RootBeanDefinition(TestBean.class);
		tbd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("testBean", tbd);

		ObjectProviderInjectionBean bean = (ObjectProviderInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isEqualTo(bf.getBean("testBean"));
		assertThat(bean.getTestBean("myName")).isEqualTo(bf.getBean("testBean", "myName"));
		assertThat(bean.getOptionalTestBean()).isEqualTo(bf.getBean("testBean"));
		assertThat(bean.getOptionalTestBeanWithDefault()).isEqualTo(bf.getBean("testBean"));
		assertThat(bean.consumeOptionalTestBean()).isEqualTo(bf.getBean("testBean"));
		assertThat(bean.getUniqueTestBean()).isEqualTo(bf.getBean("testBean"));
		assertThat(bean.getUniqueTestBeanWithDefault()).isEqualTo(bf.getBean("testBean"));
		assertThat(bean.consumeUniqueTestBean()).isEqualTo(bf.getBean("testBean"));

		List<?> testBeans = bean.iterateTestBeans();
		assertThat(testBeans).hasSize(1);
		assertThat(testBeans.contains(bf.getBean("testBean"))).isTrue();
		testBeans = bean.forEachTestBeans();
		assertThat(testBeans).hasSize(1);
		assertThat(testBeans.contains(bf.getBean("testBean"))).isTrue();
		testBeans = bean.streamTestBeans();
		assertThat(testBeans).hasSize(1);
		assertThat(testBeans.contains(bf.getBean("testBean"))).isTrue();
		testBeans = bean.sortedTestBeans();
		assertThat(testBeans).hasSize(1);
		assertThat(testBeans.contains(bf.getBean("testBean"))).isTrue();
	}

	@Test
	void objectProviderInjectionWithSingletonTarget() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ObjectProviderInjectionBean.class));
		bf.registerBeanDefinition("testBean", new RootBeanDefinition(TestBean.class));

		ObjectProviderInjectionBean bean = (ObjectProviderInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(bf.getBean("testBean"));
		assertThat(bean.getOptionalTestBean()).isSameAs(bf.getBean("testBean"));
		assertThat(bean.getOptionalTestBeanWithDefault()).isSameAs(bf.getBean("testBean"));
		assertThat(bean.consumeOptionalTestBean()).isEqualTo(bf.getBean("testBean"));
		assertThat(bean.getUniqueTestBean()).isSameAs(bf.getBean("testBean"));
		assertThat(bean.getUniqueTestBeanWithDefault()).isSameAs(bf.getBean("testBean"));
		assertThat(bean.consumeUniqueTestBean()).isEqualTo(bf.getBean("testBean"));

		List<?> testBeans = bean.iterateTestBeans();
		assertThat(testBeans).hasSize(1);
		assertThat(testBeans.contains(bf.getBean("testBean"))).isTrue();
		testBeans = bean.forEachTestBeans();
		assertThat(testBeans).hasSize(1);
		assertThat(testBeans.contains(bf.getBean("testBean"))).isTrue();
		testBeans = bean.streamTestBeans();
		assertThat(testBeans).hasSize(1);
		assertThat(testBeans.contains(bf.getBean("testBean"))).isTrue();
		testBeans = bean.sortedTestBeans();
		assertThat(testBeans).hasSize(1);
		assertThat(testBeans.contains(bf.getBean("testBean"))).isTrue();
	}

	@Test
	void objectProviderInjectionWithTargetNotAvailable() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ObjectProviderInjectionBean.class));

		ObjectProviderInjectionBean bean = (ObjectProviderInjectionBean) bf.getBean("annotatedBean");
		assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(
				bean::getTestBean);
		assertThat(bean.getOptionalTestBean()).isNull();
		assertThat(bean.consumeOptionalTestBean()).isNull();
		assertThat(bean.getOptionalTestBeanWithDefault()).isEqualTo(new TestBean("default"));
		assertThat(bean.getUniqueTestBeanWithDefault()).isEqualTo(new TestBean("default"));
		assertThat(bean.getUniqueTestBean()).isNull();
		assertThat(bean.consumeUniqueTestBean()).isNull();

		List<?> testBeans = bean.iterateTestBeans();
		assertThat(testBeans.isEmpty()).isTrue();
		testBeans = bean.forEachTestBeans();
		assertThat(testBeans.isEmpty()).isTrue();
		testBeans = bean.streamTestBeans();
		assertThat(testBeans.isEmpty()).isTrue();
		testBeans = bean.sortedTestBeans();
		assertThat(testBeans.isEmpty()).isTrue();
	}

	@Test
	void objectProviderInjectionWithTargetNotUnique() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ObjectProviderInjectionBean.class));
		bf.registerBeanDefinition("testBean1", new RootBeanDefinition(TestBean.class));
		bf.registerBeanDefinition("testBean2", new RootBeanDefinition(TestBean.class));

		ObjectProviderInjectionBean bean = (ObjectProviderInjectionBean) bf.getBean("annotatedBean");
		assertThatExceptionOfType(NoUniqueBeanDefinitionException.class).isThrownBy(bean::getTestBean);
		assertThatExceptionOfType(NoUniqueBeanDefinitionException.class).isThrownBy(bean::getOptionalTestBean);
		assertThatExceptionOfType(NoUniqueBeanDefinitionException.class).isThrownBy(bean::consumeOptionalTestBean);
		assertThat(bean.getUniqueTestBean()).isNull();
		assertThat(bean.consumeUniqueTestBean()).isNull();

		List<?> testBeans = bean.iterateTestBeans();
		assertThat(testBeans).hasSize(2);
		assertThat(testBeans.get(0)).isSameAs(bf.getBean("testBean1"));
		assertThat(testBeans.get(1)).isSameAs(bf.getBean("testBean2"));
		testBeans = bean.forEachTestBeans();
		assertThat(testBeans).hasSize(2);
		assertThat(testBeans.get(0)).isSameAs(bf.getBean("testBean1"));
		assertThat(testBeans.get(1)).isSameAs(bf.getBean("testBean2"));
		testBeans = bean.streamTestBeans();
		assertThat(testBeans).hasSize(2);
		assertThat(testBeans.get(0)).isSameAs(bf.getBean("testBean1"));
		assertThat(testBeans.get(1)).isSameAs(bf.getBean("testBean2"));
		testBeans = bean.sortedTestBeans();
		assertThat(testBeans).hasSize(2);
		assertThat(testBeans.get(0)).isSameAs(bf.getBean("testBean1"));
		assertThat(testBeans.get(1)).isSameAs(bf.getBean("testBean2"));
	}

	@Test
	void objectProviderInjectionWithTargetPrimary() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ObjectProviderInjectionBean.class));
		RootBeanDefinition tb1 = new RootBeanDefinition(TestBeanFactory.class);
		tb1.setFactoryMethodName("newTestBean1");
		tb1.setPrimary(true);
		bf.registerBeanDefinition("testBean1", tb1);
		RootBeanDefinition tb2 = new RootBeanDefinition(TestBeanFactory.class);
		tb2.setFactoryMethodName("newTestBean2");
		tb2.setLazyInit(true);
		bf.registerBeanDefinition("testBean2", tb2);

		ObjectProviderInjectionBean bean = (ObjectProviderInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.getTestBean()).isSameAs(bf.getBean("testBean1"));
		assertThat(bean.getOptionalTestBean()).isSameAs(bf.getBean("testBean1"));
		assertThat(bean.consumeOptionalTestBean()).isSameAs(bf.getBean("testBean1"));
		assertThat(bean.getUniqueTestBean()).isSameAs(bf.getBean("testBean1"));
		assertThat(bean.consumeUniqueTestBean()).isSameAs(bf.getBean("testBean1"));
		assertThat(bf.containsSingleton("testBean2")).isFalse();

		List<?> testBeans = bean.iterateTestBeans();
		assertThat(testBeans).hasSize(2);
		assertThat(testBeans.get(0)).isSameAs(bf.getBean("testBean1"));
		assertThat(testBeans.get(1)).isSameAs(bf.getBean("testBean2"));
		testBeans = bean.forEachTestBeans();
		assertThat(testBeans).hasSize(2);
		assertThat(testBeans.get(0)).isSameAs(bf.getBean("testBean1"));
		assertThat(testBeans.get(1)).isSameAs(bf.getBean("testBean2"));
		testBeans = bean.streamTestBeans();
		assertThat(testBeans).hasSize(2);
		assertThat(testBeans.get(0)).isSameAs(bf.getBean("testBean1"));
		assertThat(testBeans.get(1)).isSameAs(bf.getBean("testBean2"));
		testBeans = bean.sortedTestBeans();
		assertThat(testBeans).hasSize(2);
		assertThat(testBeans.get(0)).isSameAs(bf.getBean("testBean2"));
		assertThat(testBeans.get(1)).isSameAs(bf.getBean("testBean1"));
	}

	@Test
	void objectProviderInjectionWithUnresolvedOrderedStream() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ObjectProviderInjectionBean.class));
		RootBeanDefinition tb1 = new RootBeanDefinition(TestBeanFactory.class);
		tb1.setFactoryMethodName("newTestBean1");
		tb1.setPrimary(true);
		bf.registerBeanDefinition("testBean1", tb1);
		RootBeanDefinition tb2 = new RootBeanDefinition(TestBeanFactory.class);
		tb2.setFactoryMethodName("newTestBean2");
		tb2.setLazyInit(true);
		bf.registerBeanDefinition("testBean2", tb2);

		ObjectProviderInjectionBean bean = (ObjectProviderInjectionBean) bf.getBean("annotatedBean");
		List<?> testBeans = bean.sortedTestBeans();
		assertThat(testBeans).hasSize(2);
		assertThat(testBeans.get(0)).isSameAs(bf.getBean("testBean2"));
		assertThat(testBeans.get(1)).isSameAs(bf.getBean("testBean1"));
	}

	@Test
	void customAnnotationRequiredFieldResourceInjection() {
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationRequiredFieldResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		CustomAnnotationRequiredFieldResourceInjectionBean bean =
				(CustomAnnotationRequiredFieldResourceInjectionBean) bf.getBean("customBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
	}

	@Test
	void customAnnotationRequiredFieldResourceInjectionFailsWhenNoDependencyFound() {
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationRequiredFieldResourceInjectionBean.class));
		assertThatExceptionOfType(UnsatisfiedDependencyException.class).isThrownBy(() ->
				bf.getBean("customBean"))
			.satisfies(fieldDeclaredOn(CustomAnnotationRequiredFieldResourceInjectionBean.class));
	}

	@Test
	void customAnnotationRequiredFieldResourceInjectionFailsWhenMultipleDependenciesFound() {
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationRequiredFieldResourceInjectionBean.class));
		TestBean tb1 = new TestBean();
		bf.registerSingleton("testBean1", tb1);
		TestBean tb2 = new TestBean();
		bf.registerSingleton("testBean2", tb2);
		assertThatExceptionOfType(UnsatisfiedDependencyException.class).isThrownBy(() ->
				bf.getBean("customBean"))
			.satisfies(fieldDeclaredOn(CustomAnnotationRequiredFieldResourceInjectionBean.class));
	}

	@Test
	void customAnnotationRequiredMethodResourceInjection() {
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationRequiredMethodResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		CustomAnnotationRequiredMethodResourceInjectionBean bean =
				(CustomAnnotationRequiredMethodResourceInjectionBean) bf.getBean("customBean");
		assertThat(bean.getTestBean()).isSameAs(tb);
	}

	@Test
	void customAnnotationRequiredMethodResourceInjectionFailsWhenNoDependencyFound() {
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationRequiredMethodResourceInjectionBean.class));
		assertThatExceptionOfType(UnsatisfiedDependencyException.class).isThrownBy(() ->
				bf.getBean("customBean"))
			.satisfies(methodParameterDeclaredOn(CustomAnnotationRequiredMethodResourceInjectionBean.class));
	}

	@Test
	void customAnnotationRequiredMethodResourceInjectionFailsWhenMultipleDependenciesFound() {
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationRequiredMethodResourceInjectionBean.class));
		TestBean tb1 = new TestBean();
		bf.registerSingleton("testBean1", tb1);
		TestBean tb2 = new TestBean();
		bf.registerSingleton("testBean2", tb2);
		assertThatExceptionOfType(UnsatisfiedDependencyException.class).isThrownBy(() ->
				bf.getBean("customBean"))
			.satisfies(methodParameterDeclaredOn(CustomAnnotationRequiredMethodResourceInjectionBean.class));
	}

	@Test
	void customAnnotationOptionalFieldResourceInjection() {
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationOptionalFieldResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		CustomAnnotationOptionalFieldResourceInjectionBean bean =
				(CustomAnnotationOptionalFieldResourceInjectionBean) bf.getBean("customBean");
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean()).isNull();
		assertThat(bean.getTestBean2()).isNull();
	}

	@Test
	void customAnnotationOptionalFieldResourceInjectionWhenNoDependencyFound() {
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationOptionalFieldResourceInjectionBean.class));

		CustomAnnotationOptionalFieldResourceInjectionBean bean =
				(CustomAnnotationOptionalFieldResourceInjectionBean) bf.getBean("customBean");
		assertThat(bean.getTestBean3()).isNull();
		assertThat(bean.getTestBean()).isNull();
		assertThat(bean.getTestBean2()).isNull();
	}

	@Test
	void customAnnotationOptionalFieldResourceInjectionWhenMultipleDependenciesFound() {
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationOptionalFieldResourceInjectionBean.class));
		TestBean tb1 = new TestBean();
		bf.registerSingleton("testBean1", tb1);
		TestBean tb2 = new TestBean();
		bf.registerSingleton("testBean2", tb2);
		assertThatExceptionOfType(UnsatisfiedDependencyException.class).isThrownBy(() ->
				bf.getBean("customBean"))
			.satisfies(fieldDeclaredOn(CustomAnnotationOptionalFieldResourceInjectionBean.class));
	}

	@Test
	void customAnnotationOptionalMethodResourceInjection() {
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationOptionalMethodResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		CustomAnnotationOptionalMethodResourceInjectionBean bean =
				(CustomAnnotationOptionalMethodResourceInjectionBean) bf.getBean("customBean");
		assertThat(bean.getTestBean3()).isSameAs(tb);
		assertThat(bean.getTestBean()).isNull();
		assertThat(bean.getTestBean2()).isNull();
	}

	@Test
	void customAnnotationOptionalMethodResourceInjectionWhenNoDependencyFound() {
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationOptionalMethodResourceInjectionBean.class));

		CustomAnnotationOptionalMethodResourceInjectionBean bean =
				(CustomAnnotationOptionalMethodResourceInjectionBean) bf.getBean("customBean");
		assertThat(bean.getTestBean3()).isNull();
		assertThat(bean.getTestBean()).isNull();
		assertThat(bean.getTestBean2()).isNull();
	}

	@Test
	void customAnnotationOptionalMethodResourceInjectionWhenMultipleDependenciesFound() {
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationOptionalMethodResourceInjectionBean.class));
		TestBean tb1 = new TestBean();
		bf.registerSingleton("testBean1", tb1);
		TestBean tb2 = new TestBean();
		bf.registerSingleton("testBean2", tb2);
		assertThatExceptionOfType(UnsatisfiedDependencyException.class).isThrownBy(() ->
				bf.getBean("customBean"))
			.satisfies(methodParameterDeclaredOn(CustomAnnotationOptionalMethodResourceInjectionBean.class));
	}

	/**
	 * Verifies that a dependency on a {@link FactoryBean} can be autowired via
	 * {@link Autowired @Autowired}, specifically addressing the JIRA issue
	 * raised in <a
	 * href="https://opensource.atlassian.com/projects/spring/browse/SPR-4040"
	 * target="_blank">SPR-4040</a>.
	 */
	@Test
	void beanAutowiredWithFactoryBean() {
		bf.registerBeanDefinition("factoryBeanDependentBean", new RootBeanDefinition(FactoryBeanDependentBean.class));
		bf.registerSingleton("stringFactoryBean", new StringFactoryBean());

		final StringFactoryBean factoryBean = (StringFactoryBean) bf.getBean("&stringFactoryBean");
		final FactoryBeanDependentBean bean = (FactoryBeanDependentBean) bf.getBean("factoryBeanDependentBean");

		assertThat(factoryBean).as("The singleton StringFactoryBean should have been registered.").isNotNull();
		assertThat(bean).as("The factoryBeanDependentBean should have been registered.").isNotNull();
		assertThat(bean.getFactoryBean()).as("The FactoryBeanDependentBean should have been autowired 'by type' with the StringFactoryBean.").isEqualTo(factoryBean);
	}

	@Test
	void genericsBasedFieldInjection() {
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryFieldInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		String sv = "X";
		bf.registerSingleton("stringValue", sv);
		Integer iv = 1;
		bf.registerSingleton("integerValue", iv);
		StringRepository sr = new StringRepository();
		bf.registerSingleton("stringRepo", sr);
		IntegerRepository ir = new IntegerRepository();
		bf.registerSingleton("integerRepo", ir);

		RepositoryFieldInjectionBean bean = (RepositoryFieldInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.string).isSameAs(sv);
		assertThat(bean.integer).isSameAs(iv);
		assertThat(bean.stringArray).hasSize(1);
		assertThat(bean.integerArray).hasSize(1);
		assertThat(bean.stringArray[0]).isSameAs(sv);
		assertThat(bean.integerArray[0]).isSameAs(iv);
		assertThat(bean.stringList).hasSize(1);
		assertThat(bean.integerList).hasSize(1);
		assertThat(bean.stringList.get(0)).isSameAs(sv);
		assertThat(bean.integerList.get(0)).isSameAs(iv);
		assertThat(bean.stringMap).hasSize(1);
		assertThat(bean.integerMap).hasSize(1);
		assertThat(bean.stringMap.get("stringValue")).isSameAs(sv);
		assertThat(bean.integerMap.get("integerValue")).isSameAs(iv);
		assertThat(bean.stringRepository).isSameAs(sr);
		assertThat(bean.integerRepository).isSameAs(ir);
		assertThat(bean.stringRepositoryArray).hasSize(1);
		assertThat(bean.integerRepositoryArray).hasSize(1);
		assertThat(bean.stringRepositoryArray[0]).isSameAs(sr);
		assertThat(bean.integerRepositoryArray[0]).isSameAs(ir);
		assertThat(bean.stringRepositoryList).hasSize(1);
		assertThat(bean.integerRepositoryList).hasSize(1);
		assertThat(bean.stringRepositoryList.get(0)).isSameAs(sr);
		assertThat(bean.integerRepositoryList.get(0)).isSameAs(ir);
		assertThat(bean.stringRepositoryMap).hasSize(1);
		assertThat(bean.integerRepositoryMap).hasSize(1);
		assertThat(bean.stringRepositoryMap.get("stringRepo")).isSameAs(sr);
		assertThat(bean.integerRepositoryMap.get("integerRepo")).isSameAs(ir);
	}

	@Test
	void genericsBasedFieldInjectionWithSubstitutedVariables() {
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryFieldInjectionBeanWithSubstitutedVariables.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		String sv = "X";
		bf.registerSingleton("stringValue", sv);
		Integer iv = 1;
		bf.registerSingleton("integerValue", iv);
		StringRepository sr = new StringRepository();
		bf.registerSingleton("stringRepo", sr);
		IntegerRepository ir = new IntegerRepository();
		bf.registerSingleton("integerRepo", ir);

		RepositoryFieldInjectionBeanWithSubstitutedVariables bean = (RepositoryFieldInjectionBeanWithSubstitutedVariables) bf.getBean("annotatedBean");
		assertThat(bean.string).isSameAs(sv);
		assertThat(bean.integer).isSameAs(iv);
		assertThat(bean.stringArray).hasSize(1);
		assertThat(bean.integerArray).hasSize(1);
		assertThat(bean.stringArray[0]).isSameAs(sv);
		assertThat(bean.integerArray[0]).isSameAs(iv);
		assertThat(bean.stringList).hasSize(1);
		assertThat(bean.integerList).hasSize(1);
		assertThat(bean.stringList.get(0)).isSameAs(sv);
		assertThat(bean.integerList.get(0)).isSameAs(iv);
		assertThat(bean.stringMap).hasSize(1);
		assertThat(bean.integerMap).hasSize(1);
		assertThat(bean.stringMap.get("stringValue")).isSameAs(sv);
		assertThat(bean.integerMap.get("integerValue")).isSameAs(iv);
		assertThat(bean.stringRepository).isSameAs(sr);
		assertThat(bean.integerRepository).isSameAs(ir);
		assertThat(bean.stringRepositoryArray).hasSize(1);
		assertThat(bean.integerRepositoryArray).hasSize(1);
		assertThat(bean.stringRepositoryArray[0]).isSameAs(sr);
		assertThat(bean.integerRepositoryArray[0]).isSameAs(ir);
		assertThat(bean.stringRepositoryList).hasSize(1);
		assertThat(bean.integerRepositoryList).hasSize(1);
		assertThat(bean.stringRepositoryList.get(0)).isSameAs(sr);
		assertThat(bean.integerRepositoryList.get(0)).isSameAs(ir);
		assertThat(bean.stringRepositoryMap).hasSize(1);
		assertThat(bean.integerRepositoryMap).hasSize(1);
		assertThat(bean.stringRepositoryMap.get("stringRepo")).isSameAs(sr);
		assertThat(bean.integerRepositoryMap.get("integerRepo")).isSameAs(ir);
	}

	@Test
	void genericsBasedFieldInjectionWithQualifiers() {
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryFieldInjectionBeanWithQualifiers.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		StringRepository sr = new StringRepository();
		bf.registerSingleton("stringRepo", sr);
		IntegerRepository ir = new IntegerRepository();
		bf.registerSingleton("integerRepo", ir);

		RepositoryFieldInjectionBeanWithQualifiers bean = (RepositoryFieldInjectionBeanWithQualifiers) bf.getBean("annotatedBean");
		assertThat(bean.stringRepository).isSameAs(sr);
		assertThat(bean.integerRepository).isSameAs(ir);
		assertThat(bean.stringRepositoryArray).hasSize(1);
		assertThat(bean.integerRepositoryArray).hasSize(1);
		assertThat(bean.stringRepositoryArray[0]).isSameAs(sr);
		assertThat(bean.integerRepositoryArray[0]).isSameAs(ir);
		assertThat(bean.stringRepositoryList).hasSize(1);
		assertThat(bean.integerRepositoryList).hasSize(1);
		assertThat(bean.stringRepositoryList.get(0)).isSameAs(sr);
		assertThat(bean.integerRepositoryList.get(0)).isSameAs(ir);
		assertThat(bean.stringRepositoryMap).hasSize(1);
		assertThat(bean.integerRepositoryMap).hasSize(1);
		assertThat(bean.stringRepositoryMap.get("stringRepo")).isSameAs(sr);
		assertThat(bean.integerRepositoryMap.get("integerRepo")).isSameAs(ir);
	}

	@Test
	void genericsBasedFieldInjectionWithMocks() {
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryFieldInjectionBeanWithQualifiers.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);

		RootBeanDefinition rbd = new RootBeanDefinition(MocksControl.class);
		bf.registerBeanDefinition("mocksControl", rbd);
		rbd = new RootBeanDefinition();
		rbd.setFactoryBeanName("mocksControl");
		rbd.setFactoryMethodName("createMock");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(Repository.class);
		bf.registerBeanDefinition("stringRepo", rbd);
		rbd = new RootBeanDefinition();
		rbd.setFactoryBeanName("mocksControl");
		rbd.setFactoryMethodName("createMock");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(Repository.class);
		rbd.setQualifiedElement(ReflectionUtils.findField(getClass(), "integerRepositoryQualifierProvider"));
		bf.registerBeanDefinition("integerRepository", rbd); // Bean name not matching qualifier

		RepositoryFieldInjectionBeanWithQualifiers bean = (RepositoryFieldInjectionBeanWithQualifiers) bf.getBean("annotatedBean");
		Repository<?> sr = bf.getBean("stringRepo", Repository.class);
		Repository<?> ir = bf.getBean("integerRepository", Repository.class);
		assertThat(bean.stringRepository).isSameAs(sr);
		assertThat(bean.integerRepository).isSameAs(ir);
		assertThat(bean.stringRepositoryArray).hasSize(1);
		assertThat(bean.integerRepositoryArray).hasSize(1);
		assertThat(bean.stringRepositoryArray[0]).isSameAs(sr);
		assertThat(bean.integerRepositoryArray[0]).isSameAs(ir);
		assertThat(bean.stringRepositoryList).hasSize(1);
		assertThat(bean.integerRepositoryList).hasSize(1);
		assertThat(bean.stringRepositoryList.get(0)).isSameAs(sr);
		assertThat(bean.integerRepositoryList.get(0)).isSameAs(ir);
		assertThat(bean.stringRepositoryMap).hasSize(1);
		assertThat(bean.integerRepositoryMap).hasSize(1);
		assertThat(bean.stringRepositoryMap.get("stringRepo")).isSameAs(sr);
		assertThat(bean.integerRepositoryMap.get("integerRepository")).isSameAs(ir);
	}

	@Test
	void genericsBasedFieldInjectionWithSimpleMatch() {
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryFieldInjectionBeanWithSimpleMatch.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);

		bf.registerSingleton("repo", new StringRepository());

		RepositoryFieldInjectionBeanWithSimpleMatch bean = (RepositoryFieldInjectionBeanWithSimpleMatch) bf.getBean("annotatedBean");
		Repository<?> repo = bf.getBean("repo", Repository.class);
		assertThat(bean.repository).isSameAs(repo);
		assertThat(bean.stringRepository).isSameAs(repo);
		assertThat(bean.repositoryArray).hasSize(1);
		assertThat(bean.stringRepositoryArray).hasSize(1);
		assertThat(bean.repositoryArray[0]).isSameAs(repo);
		assertThat(bean.stringRepositoryArray[0]).isSameAs(repo);
		assertThat(bean.repositoryList).hasSize(1);
		assertThat(bean.stringRepositoryList).hasSize(1);
		assertThat(bean.repositoryList.get(0)).isSameAs(repo);
		assertThat(bean.stringRepositoryList.get(0)).isSameAs(repo);
		assertThat(bean.repositoryMap).hasSize(1);
		assertThat(bean.stringRepositoryMap).hasSize(1);
		assertThat(bean.repositoryMap.get("repo")).isSameAs(repo);
		assertThat(bean.stringRepositoryMap.get("repo")).isSameAs(repo);

		assertThat(bf.getBeanNamesForType(ResolvableType.forClassWithGenerics(Repository.class, String.class))).isEqualTo(new String[] {"repo"});
	}

	@Test
	void genericsBasedFactoryBeanInjectionWithBeanDefinition() {
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryFactoryBeanInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		bf.registerBeanDefinition("repoFactoryBean", new RootBeanDefinition(RepositoryFactoryBean.class));

		RepositoryFactoryBeanInjectionBean bean = (RepositoryFactoryBeanInjectionBean) bf.getBean("annotatedBean");
		RepositoryFactoryBean<?> repoFactoryBean = bf.getBean("&repoFactoryBean", RepositoryFactoryBean.class);
		assertThat(bean.repositoryFactoryBean).isSameAs(repoFactoryBean);
	}

	@Test
	void genericsBasedFactoryBeanInjectionWithSingletonBean() {
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryFactoryBeanInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		bf.registerSingleton("repoFactoryBean", new RepositoryFactoryBean<>());

		RepositoryFactoryBeanInjectionBean bean = (RepositoryFactoryBeanInjectionBean) bf.getBean("annotatedBean");
		RepositoryFactoryBean<?> repoFactoryBean = bf.getBean("&repoFactoryBean", RepositoryFactoryBean.class);
		assertThat(bean.repositoryFactoryBean).isSameAs(repoFactoryBean);
	}

	@Test
	void genericsBasedFieldInjectionWithSimpleMatchAndMock() {
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryFieldInjectionBeanWithSimpleMatch.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);

		RootBeanDefinition rbd = new RootBeanDefinition(MocksControl.class);
		bf.registerBeanDefinition("mocksControl", rbd);
		rbd = new RootBeanDefinition();
		rbd.setFactoryBeanName("mocksControl");
		rbd.setFactoryMethodName("createMock");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(Repository.class);
		bf.registerBeanDefinition("repo", rbd);

		RepositoryFieldInjectionBeanWithSimpleMatch bean = (RepositoryFieldInjectionBeanWithSimpleMatch) bf.getBean("annotatedBean");
		Repository<?> repo = bf.getBean("repo", Repository.class);
		assertThat(bean.repository).isSameAs(repo);
		assertThat(bean.stringRepository).isSameAs(repo);
		assertThat(bean.repositoryArray).hasSize(1);
		assertThat(bean.stringRepositoryArray).hasSize(1);
		assertThat(bean.repositoryArray[0]).isSameAs(repo);
		assertThat(bean.stringRepositoryArray[0]).isSameAs(repo);
		assertThat(bean.repositoryList).hasSize(1);
		assertThat(bean.stringRepositoryList).hasSize(1);
		assertThat(bean.repositoryList.get(0)).isSameAs(repo);
		assertThat(bean.stringRepositoryList.get(0)).isSameAs(repo);
		assertThat(bean.repositoryMap).hasSize(1);
		assertThat(bean.stringRepositoryMap).hasSize(1);
		assertThat(bean.repositoryMap.get("repo")).isSameAs(repo);
		assertThat(bean.stringRepositoryMap.get("repo")).isSameAs(repo);
	}

	@Test
	void genericsBasedFieldInjectionWithSimpleMatchAndMockito() {
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryFieldInjectionBeanWithSimpleMatch.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);

		RootBeanDefinition rbd = new RootBeanDefinition();
		rbd.setBeanClassName(getClass().getName());
		rbd.setFactoryMethodName("createMockitoMock");
		// TypedStringValue used to be equivalent to an XML-defined argument String
		rbd.getConstructorArgumentValues().addGenericArgumentValue(new TypedStringValue(Repository.class.getName()));
		bf.registerBeanDefinition("repo", rbd);

		RepositoryFieldInjectionBeanWithSimpleMatch bean = (RepositoryFieldInjectionBeanWithSimpleMatch) bf.getBean("annotatedBean");
		Repository<?> repo = bf.getBean("repo", Repository.class);
		assertThat(bean.repository).isSameAs(repo);
		assertThat(bean.stringRepository).isSameAs(repo);
		assertThat(bean.repositoryArray).hasSize(1);
		assertThat(bean.stringRepositoryArray).hasSize(1);
		assertThat(bean.repositoryArray[0]).isSameAs(repo);
		assertThat(bean.stringRepositoryArray[0]).isSameAs(repo);
		assertThat(bean.repositoryList).hasSize(1);
		assertThat(bean.stringRepositoryList).hasSize(1);
		assertThat(bean.repositoryList.get(0)).isSameAs(repo);
		assertThat(bean.stringRepositoryList.get(0)).isSameAs(repo);
		assertThat(bean.repositoryMap).hasSize(1);
		assertThat(bean.stringRepositoryMap).hasSize(1);
		assertThat(bean.repositoryMap.get("repo")).isSameAs(repo);
		assertThat(bean.stringRepositoryMap.get("repo")).isSameAs(repo);
	}

	/**
	 * Mimics and delegates to {@link Mockito#mock(Class)} -- created here to avoid factory
	 * method resolution issues caused by the introduction of {@code Mockito.mock(T...)}
	 * in Mockito 4.10.
	 */
	public static <T> T createMockitoMock(Class<T> classToMock) {
		return Mockito.mock(classToMock);
	}

	@Test
	void genericsBasedMethodInjection() {
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryMethodInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		String sv = "X";
		bf.registerSingleton("stringValue", sv);
		Integer iv = 1;
		bf.registerSingleton("integerValue", iv);
		StringRepository sr = new StringRepository();
		bf.registerSingleton("stringRepo", sr);
		IntegerRepository ir = new IntegerRepository();
		bf.registerSingleton("integerRepo", ir);

		RepositoryMethodInjectionBean bean = (RepositoryMethodInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.string).isSameAs(sv);
		assertThat(bean.integer).isSameAs(iv);
		assertThat(bean.stringArray).hasSize(1);
		assertThat(bean.integerArray).hasSize(1);
		assertThat(bean.stringArray[0]).isSameAs(sv);
		assertThat(bean.integerArray[0]).isSameAs(iv);
		assertThat(bean.stringList).hasSize(1);
		assertThat(bean.integerList).hasSize(1);
		assertThat(bean.stringList.get(0)).isSameAs(sv);
		assertThat(bean.integerList.get(0)).isSameAs(iv);
		assertThat(bean.stringMap).hasSize(1);
		assertThat(bean.integerMap).hasSize(1);
		assertThat(bean.stringMap.get("stringValue")).isSameAs(sv);
		assertThat(bean.integerMap.get("integerValue")).isSameAs(iv);
		assertThat(bean.stringRepository).isSameAs(sr);
		assertThat(bean.integerRepository).isSameAs(ir);
		assertThat(bean.stringRepositoryArray).hasSize(1);
		assertThat(bean.integerRepositoryArray).hasSize(1);
		assertThat(bean.stringRepositoryArray[0]).isSameAs(sr);
		assertThat(bean.integerRepositoryArray[0]).isSameAs(ir);
		assertThat(bean.stringRepositoryList).hasSize(1);
		assertThat(bean.integerRepositoryList).hasSize(1);
		assertThat(bean.stringRepositoryList.get(0)).isSameAs(sr);
		assertThat(bean.integerRepositoryList.get(0)).isSameAs(ir);
		assertThat(bean.stringRepositoryMap).hasSize(1);
		assertThat(bean.integerRepositoryMap).hasSize(1);
		assertThat(bean.stringRepositoryMap.get("stringRepo")).isSameAs(sr);
		assertThat(bean.integerRepositoryMap.get("integerRepo")).isSameAs(ir);
	}

	@Test
	void genericsBasedMethodInjectionWithSubstitutedVariables() {
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryMethodInjectionBeanWithSubstitutedVariables.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		String sv = "X";
		bf.registerSingleton("stringValue", sv);
		Integer iv = 1;
		bf.registerSingleton("integerValue", iv);
		StringRepository sr = new StringRepository();
		bf.registerSingleton("stringRepo", sr);
		IntegerRepository ir = new IntegerRepository();
		bf.registerSingleton("integerRepo", ir);

		RepositoryMethodInjectionBeanWithSubstitutedVariables bean = (RepositoryMethodInjectionBeanWithSubstitutedVariables) bf.getBean("annotatedBean");
		assertThat(bean.string).isSameAs(sv);
		assertThat(bean.integer).isSameAs(iv);
		assertThat(bean.stringArray).hasSize(1);
		assertThat(bean.integerArray).hasSize(1);
		assertThat(bean.stringArray[0]).isSameAs(sv);
		assertThat(bean.integerArray[0]).isSameAs(iv);
		assertThat(bean.stringList).hasSize(1);
		assertThat(bean.integerList).hasSize(1);
		assertThat(bean.stringList.get(0)).isSameAs(sv);
		assertThat(bean.integerList.get(0)).isSameAs(iv);
		assertThat(bean.stringMap).hasSize(1);
		assertThat(bean.integerMap).hasSize(1);
		assertThat(bean.stringMap.get("stringValue")).isSameAs(sv);
		assertThat(bean.integerMap.get("integerValue")).isSameAs(iv);
		assertThat(bean.stringRepository).isSameAs(sr);
		assertThat(bean.integerRepository).isSameAs(ir);
		assertThat(bean.stringRepositoryArray).hasSize(1);
		assertThat(bean.integerRepositoryArray).hasSize(1);
		assertThat(bean.stringRepositoryArray[0]).isSameAs(sr);
		assertThat(bean.integerRepositoryArray[0]).isSameAs(ir);
		assertThat(bean.stringRepositoryList).hasSize(1);
		assertThat(bean.integerRepositoryList).hasSize(1);
		assertThat(bean.stringRepositoryList.get(0)).isSameAs(sr);
		assertThat(bean.integerRepositoryList.get(0)).isSameAs(ir);
		assertThat(bean.stringRepositoryMap).hasSize(1);
		assertThat(bean.integerRepositoryMap).hasSize(1);
		assertThat(bean.stringRepositoryMap.get("stringRepo")).isSameAs(sr);
		assertThat(bean.integerRepositoryMap.get("integerRepo")).isSameAs(ir);
	}

	@Test
	void genericsBasedConstructorInjection() {
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryConstructorInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		StringRepository sr = new StringRepository();
		bf.registerSingleton("stringRepo", sr);
		IntegerRepository ir = new IntegerRepository();
		bf.registerSingleton("integerRepo", ir);

		RepositoryConstructorInjectionBean bean = (RepositoryConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.stringRepository).isSameAs(sr);
		assertThat(bean.integerRepository).isSameAs(ir);
		assertThat(bean.stringRepositoryArray).hasSize(1);
		assertThat(bean.integerRepositoryArray).hasSize(1);
		assertThat(bean.stringRepositoryArray[0]).isSameAs(sr);
		assertThat(bean.integerRepositoryArray[0]).isSameAs(ir);
		assertThat(bean.stringRepositoryList).hasSize(1);
		assertThat(bean.integerRepositoryList).hasSize(1);
		assertThat(bean.stringRepositoryList.get(0)).isSameAs(sr);
		assertThat(bean.integerRepositoryList.get(0)).isSameAs(ir);
		assertThat(bean.stringRepositoryMap).hasSize(1);
		assertThat(bean.integerRepositoryMap).hasSize(1);
		assertThat(bean.stringRepositoryMap.get("stringRepo")).isSameAs(sr);
		assertThat(bean.integerRepositoryMap.get("integerRepo")).isSameAs(ir);
	}

	@Test
	@SuppressWarnings("rawtypes")
	void genericsBasedConstructorInjectionWithNonTypedTarget() {
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryConstructorInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		GenericRepository gr = new GenericRepository();
		bf.registerSingleton("genericRepo", gr);

		RepositoryConstructorInjectionBean bean = (RepositoryConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.stringRepository).isSameAs(gr);
		assertThat(bean.integerRepository).isSameAs(gr);
		assertThat(bean.stringRepositoryArray).hasSize(1);
		assertThat(bean.integerRepositoryArray).hasSize(1);
		assertThat(bean.stringRepositoryArray[0]).isSameAs(gr);
		assertThat(bean.integerRepositoryArray[0]).isSameAs(gr);
		assertThat(bean.stringRepositoryList).hasSize(1);
		assertThat(bean.integerRepositoryList).hasSize(1);
		assertThat(bean.stringRepositoryList.get(0)).isSameAs(gr);
		assertThat(bean.integerRepositoryList.get(0)).isSameAs(gr);
		assertThat(bean.stringRepositoryMap).hasSize(1);
		assertThat(bean.integerRepositoryMap).hasSize(1);
		assertThat(bean.stringRepositoryMap.get("genericRepo")).isSameAs(gr);
		assertThat(bean.integerRepositoryMap.get("genericRepo")).isSameAs(gr);
	}

	@Test
	void genericsBasedConstructorInjectionWithNonGenericTarget() {
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryConstructorInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		SimpleRepository ngr = new SimpleRepository();
		bf.registerSingleton("simpleRepo", ngr);

		RepositoryConstructorInjectionBean bean = (RepositoryConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.stringRepository).isSameAs(ngr);
		assertThat(bean.integerRepository).isSameAs(ngr);
		assertThat(bean.stringRepositoryArray).hasSize(1);
		assertThat(bean.integerRepositoryArray).hasSize(1);
		assertThat(bean.stringRepositoryArray[0]).isSameAs(ngr);
		assertThat(bean.integerRepositoryArray[0]).isSameAs(ngr);
		assertThat(bean.stringRepositoryList).hasSize(1);
		assertThat(bean.integerRepositoryList).hasSize(1);
		assertThat(bean.stringRepositoryList.get(0)).isSameAs(ngr);
		assertThat(bean.integerRepositoryList.get(0)).isSameAs(ngr);
		assertThat(bean.stringRepositoryMap).hasSize(1);
		assertThat(bean.integerRepositoryMap).hasSize(1);
		assertThat(bean.stringRepositoryMap.get("simpleRepo")).isSameAs(ngr);
		assertThat(bean.integerRepositoryMap.get("simpleRepo")).isSameAs(ngr);
	}

	@Test
	@SuppressWarnings("rawtypes")
	void genericsBasedConstructorInjectionWithMixedTargets() {
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryConstructorInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		StringRepository sr = new StringRepository();
		bf.registerSingleton("stringRepo", sr);
		GenericRepository gr = new GenericRepositorySubclass();
		bf.registerSingleton("genericRepo", gr);

		RepositoryConstructorInjectionBean bean = (RepositoryConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.stringRepository).isSameAs(sr);
		assertThat(bean.integerRepository).isSameAs(gr);
		assertThat(bean.stringRepositoryArray).hasSize(1);
		assertThat(bean.integerRepositoryArray).hasSize(1);
		assertThat(bean.stringRepositoryArray[0]).isSameAs(sr);
		assertThat(bean.integerRepositoryArray[0]).isSameAs(gr);
		assertThat(bean.stringRepositoryList).hasSize(1);
		assertThat(bean.integerRepositoryList).hasSize(1);
		assertThat(bean.stringRepositoryList.get(0)).isSameAs(sr);
		assertThat(bean.integerRepositoryList.get(0)).isSameAs(gr);
		assertThat(bean.stringRepositoryMap).hasSize(1);
		assertThat(bean.integerRepositoryMap).hasSize(1);
		assertThat(bean.stringRepositoryMap.get("stringRepo")).isSameAs(sr);
		assertThat(bean.integerRepositoryMap.get("genericRepo")).isSameAs(gr);
	}

	@Test
	void genericsBasedConstructorInjectionWithMixedTargetsIncludingNonGeneric() {
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryConstructorInjectionBean.class);
		bd.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		StringRepository sr = new StringRepository();
		bf.registerSingleton("stringRepo", sr);
		SimpleRepository ngr = new SimpleRepositorySubclass();
		bf.registerSingleton("simpleRepo", ngr);

		RepositoryConstructorInjectionBean bean = (RepositoryConstructorInjectionBean) bf.getBean("annotatedBean");
		assertThat(bean.stringRepository).isSameAs(sr);
		assertThat(bean.integerRepository).isSameAs(ngr);
		assertThat(bean.stringRepositoryArray).hasSize(1);
		assertThat(bean.integerRepositoryArray).hasSize(1);
		assertThat(bean.stringRepositoryArray[0]).isSameAs(sr);
		assertThat(bean.integerRepositoryArray[0]).isSameAs(ngr);
		assertThat(bean.stringRepositoryList).hasSize(1);
		assertThat(bean.integerRepositoryList).hasSize(1);
		assertThat(bean.stringRepositoryList.get(0)).isSameAs(sr);
		assertThat(bean.integerRepositoryList.get(0)).isSameAs(ngr);
		assertThat(bean.stringRepositoryMap).hasSize(1);
		assertThat(bean.integerRepositoryMap).hasSize(1);
		assertThat(bean.stringRepositoryMap.get("stringRepo")).isSameAs(sr);
		assertThat(bean.integerRepositoryMap.get("simpleRepo")).isSameAs(ngr);
	}

	@Test
	@SuppressWarnings("rawtypes")
	void genericsBasedInjectionIntoMatchingTypeVariable() {
		RootBeanDefinition bd = new RootBeanDefinition(GenericInterface1Impl.class);
		bd.setFactoryMethodName("create");
		bf.registerBeanDefinition("bean1", bd);
		bf.registerBeanDefinition("bean2", new RootBeanDefinition(GenericInterface2Impl.class));

		GenericInterface1Impl bean1 = (GenericInterface1Impl) bf.getBean("bean1");
		GenericInterface2Impl bean2 = (GenericInterface2Impl) bf.getBean("bean2");
		assertThat(bean1.gi2).isSameAs(bean2);
		assertThat(bd.getResolvableType()).isEqualTo(ResolvableType.forClass(GenericInterface1Impl.class));
	}

	@Test
	@SuppressWarnings("rawtypes")
	void genericsBasedInjectionIntoUnresolvedTypeVariable() {
		RootBeanDefinition bd = new RootBeanDefinition(GenericInterface1Impl.class);
		bd.setFactoryMethodName("createPlain");
		bf.registerBeanDefinition("bean1", bd);
		bf.registerBeanDefinition("bean2", new RootBeanDefinition(GenericInterface2Impl.class));

		GenericInterface1Impl bean1 = (GenericInterface1Impl) bf.getBean("bean1");
		GenericInterface2Impl bean2 = (GenericInterface2Impl) bf.getBean("bean2");
		assertThat(bean1.gi2).isSameAs(bean2);
		assertThat(bd.getResolvableType()).isEqualTo(ResolvableType.forClass(GenericInterface1Impl.class));
	}

	@Test
	@SuppressWarnings("rawtypes")
	void genericsBasedInjectionIntoTypeVariableSelectingBestMatch() {
		RootBeanDefinition bd = new RootBeanDefinition(GenericInterface1Impl.class);
		bd.setFactoryMethodName("create");
		bf.registerBeanDefinition("bean1", bd);
		bf.registerBeanDefinition("bean2", new RootBeanDefinition(GenericInterface2Impl.class));
		bf.registerBeanDefinition("bean2a", new RootBeanDefinition(ReallyGenericInterface2Impl.class));
		bf.registerBeanDefinition("bean2b", new RootBeanDefinition(PlainGenericInterface2Impl.class));

		GenericInterface1Impl bean1 = (GenericInterface1Impl) bf.getBean("bean1");
		GenericInterface2Impl bean2 = (GenericInterface2Impl) bf.getBean("bean2");
		assertThat(bean1.gi2).isSameAs(bean2);
		assertThat(bf.getBeanNamesForType(ResolvableType.forClassWithGenerics(GenericInterface1.class, String.class))).isEqualTo(new String[] {"bean1"});
		assertThat(bf.getBeanNamesForType(ResolvableType.forClassWithGenerics(GenericInterface2.class, String.class))).isEqualTo(new String[] {"bean2"});
	}

	@Test
	@Disabled  // SPR-11521
	@SuppressWarnings("rawtypes")
	void genericsBasedInjectionIntoTypeVariableSelectingBestMatchAgainstFactoryMethodSignature() {
		RootBeanDefinition bd = new RootBeanDefinition(GenericInterface1Impl.class);
		bd.setFactoryMethodName("createErased");
		bf.registerBeanDefinition("bean1", bd);
		bf.registerBeanDefinition("bean2", new RootBeanDefinition(GenericInterface2Impl.class));
		bf.registerBeanDefinition("bean2a", new RootBeanDefinition(ReallyGenericInterface2Impl.class));
		bf.registerBeanDefinition("bean2b", new RootBeanDefinition(PlainGenericInterface2Impl.class));

		GenericInterface1Impl bean1 = (GenericInterface1Impl) bf.getBean("bean1");
		GenericInterface2Impl bean2 = (GenericInterface2Impl) bf.getBean("bean2");
		assertThat(bean1.gi2).isSameAs(bean2);
	}

	@Test
	void genericsBasedInjectionWithBeanDefinitionTargetResolvableType() {
		RootBeanDefinition bd1 = new RootBeanDefinition(GenericInterface2Bean.class);
		bd1.setTargetType(ResolvableType.forClassWithGenerics(GenericInterface2Bean.class, String.class));
		bf.registerBeanDefinition("bean1", bd1);
		RootBeanDefinition bd2 = new RootBeanDefinition(GenericInterface2Bean.class);
		bd2.setTargetType(ResolvableType.forClassWithGenerics(GenericInterface2Bean.class, Integer.class));
		bf.registerBeanDefinition("bean2", bd2);
		bf.registerBeanDefinition("bean3", new RootBeanDefinition(MultiGenericFieldInjection.class));

		assertThat(bf.getBean("bean3").toString()).isEqualTo("bean1 a bean2 123");
		assertThat(bd1.getResolvableType()).isEqualTo(ResolvableType.forClassWithGenerics(GenericInterface2Bean.class, String.class));
		assertThat(bd2.getResolvableType()).isEqualTo(ResolvableType.forClassWithGenerics(GenericInterface2Bean.class, Integer.class));
	}

	@Test
	void circularTypeReference() {
		bf.registerBeanDefinition("bean1", new RootBeanDefinition(StockServiceImpl.class));
		bf.registerBeanDefinition("bean2", new RootBeanDefinition(StockMovementDaoImpl.class));
		bf.registerBeanDefinition("bean3", new RootBeanDefinition(StockMovementImpl.class));
		bf.registerBeanDefinition("bean4", new RootBeanDefinition(StockMovementInstructionImpl.class));

		StockServiceImpl service = bf.getBean(StockServiceImpl.class);
		assertThat(service.stockMovementDao).isSameAs(bf.getBean(StockMovementDaoImpl.class));
	}

	@Test
	void bridgeMethodHandling() {
		bf.registerBeanDefinition("bean1", new RootBeanDefinition(MyCallable.class));
		bf.registerBeanDefinition("bean2", new RootBeanDefinition(SecondCallable.class));
		bf.registerBeanDefinition("bean3", new RootBeanDefinition(FooBar.class));
		assertThat(bf.getBean(FooBar.class)).isNotNull();
	}

	@Test
	void singleConstructorWithProvidedArgument() {
		RootBeanDefinition bd = new RootBeanDefinition(ProvidedArgumentBean.class);
		bd.getConstructorArgumentValues().addGenericArgumentValue(Collections.singletonList("value"));
		bf.registerBeanDefinition("beanWithArgs", bd);
		assertThat(bf.getBean(ProvidedArgumentBean.class)).isNotNull();
	}

	@Test
	void annotatedDefaultConstructor() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(AnnotatedDefaultConstructorBean.class));

		assertThat(bf.getBean("annotatedBean")).isNotNull();
	}

	@Test  // SPR-15125
	void factoryBeanSelfInjection() {
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(SelfInjectingFactoryBean.class));

		SelfInjectingFactoryBean bean = bf.getBean(SelfInjectingFactoryBean.class);
		assertThat(bean.testBean).isSameAs(bf.getBean("annotatedBean"));
	}

	@Test  // SPR-15125
	void factoryBeanSelfInjectionViaFactoryMethod() {
		RootBeanDefinition bd = new RootBeanDefinition(SelfInjectingFactoryBean.class);
		bd.setFactoryMethodName("create");
		bf.registerBeanDefinition("annotatedBean", bd);

		SelfInjectingFactoryBean bean = bf.getBean(SelfInjectingFactoryBean.class);
		assertThat(bean.testBean).isSameAs(bf.getBean("annotatedBean"));
	}

	private <E extends UnsatisfiedDependencyException> Consumer<E> methodParameterDeclaredOn(
			Class<?> expected) {
		return declaredOn(
				injectionPoint -> injectionPoint.getMethodParameter().getDeclaringClass(),
				expected);
	}

	private <E extends UnsatisfiedDependencyException> Consumer<E> fieldDeclaredOn(
			Class<?> expected) {
		return declaredOn(
				injectionPoint -> injectionPoint.getField().getDeclaringClass(),
				expected);
	}

	private <E extends UnsatisfiedDependencyException> Consumer<E> declaredOn(
			Function<InjectionPoint, Class<?>> declaringClassExtractor,
			Class<?> expected) {
		return ex -> {
			InjectionPoint injectionPoint = ex.getInjectionPoint();
			Class<?> declaringClass = declaringClassExtractor.apply(injectionPoint);
			assertThat(declaringClass).isSameAs(expected);
		};
	}


	@Qualifier("testBean")
	private void testBeanQualifierProvider() {}

	@Qualifier("integerRepo")
	private Repository<?> integerRepositoryQualifierProvider;


	public static class ResourceInjectionBean {

		@Autowired(required = false)
		private TestBean testBean;

		private TestBean testBean2;

		@Autowired
		public void setTestBean2(TestBean testBean2) {
			if (this.testBean2 != null) {
				throw new IllegalStateException("Already called");
			}
			this.testBean2 = testBean2;
		}

		public TestBean getTestBean() {
			return this.testBean;
		}

		public TestBean getTestBean2() {
			return this.testBean2;
		}
	}


	static class NonPublicResourceInjectionBean<T> extends ResourceInjectionBean {

		@Autowired
		public final ITestBean testBean3 = null;

		private T nestedTestBean;

		private ITestBean testBean4;

		protected BeanFactory beanFactory;

		public boolean baseInjected = false;

		public NonPublicResourceInjectionBean() {
		}

		@Override
		@Autowired
		@SuppressWarnings("deprecation")
		public void setTestBean2(TestBean testBean2) {
			super.setTestBean2(testBean2);
		}

		@Autowired
		private void inject(ITestBean testBean4, T nestedTestBean) {
			this.testBean4 = testBean4;
			this.nestedTestBean = nestedTestBean;
		}

		@Autowired
		private void inject(ITestBean testBean4) {
			this.baseInjected = true;
		}

		@Autowired
		protected void initBeanFactory(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		public ITestBean getTestBean3() {
			return this.testBean3;
		}

		public ITestBean getTestBean4() {
			return this.testBean4;
		}

		public T getNestedTestBean() {
			return this.nestedTestBean;
		}

		public BeanFactory getBeanFactory() {
			return this.beanFactory;
		}
	}


	public static class TypedExtendedResourceInjectionBean extends NonPublicResourceInjectionBean<NestedTestBean>
			implements DisposableBean {

		public boolean destroyed = false;

		@Override
		public void destroy() {
			this.destroyed = true;
		}
	}


	public static class OverriddenExtendedResourceInjectionBean extends NonPublicResourceInjectionBean<NestedTestBean> {

		public boolean subInjected = false;

		@Override
		public void setTestBean2(TestBean testBean2) {
			super.setTestBean2(testBean2);
		}

		@Override
		protected void initBeanFactory(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		@Autowired
		private void inject(ITestBean testBean4) {
			this.subInjected = true;
		}
	}


	public interface InterfaceWithDefaultMethod {

		@Autowired
		void setTestBean2(TestBean testBean2);

		@Autowired
		default void injectDefault(ITestBean testBean4) {
			markSubInjected();
		}

		void markSubInjected();
	}


	public static class DefaultMethodResourceInjectionBean extends NonPublicResourceInjectionBean<NestedTestBean>
			implements InterfaceWithDefaultMethod {

		public boolean subInjected = false;

		@Override
		public void setTestBean2(TestBean testBean2) {
			super.setTestBean2(testBean2);
		}

		@Override
		protected void initBeanFactory(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		@Override
		public void markSubInjected() {
			subInjected = true;
		}
	}


	public static class OptionalResourceInjectionBean extends ResourceInjectionBean {

		@Autowired(required = false)
		protected ITestBean testBean3;

		private IndexedTestBean indexedTestBean;

		private NestedTestBean[] nestedTestBeans;

		@Autowired(required = false)
		public NestedTestBean[] nestedTestBeansField;

		private ITestBean testBean4;

		@Override
		@Autowired(required = false)
		public void setTestBean2(TestBean testBean2) {
			super.setTestBean2(testBean2);
		}

		@Autowired(required = false)
		private void inject(ITestBean testBean4, NestedTestBean[] nestedTestBeans, IndexedTestBean indexedTestBean) {
			this.testBean4 = testBean4;
			this.indexedTestBean = indexedTestBean;
			this.nestedTestBeans = nestedTestBeans;
		}

		public ITestBean getTestBean3() {
			return this.testBean3;
		}

		public ITestBean getTestBean4() {
			return this.testBean4;
		}

		public IndexedTestBean getIndexedTestBean() {
			return this.indexedTestBean;
		}

		public NestedTestBean[] getNestedTestBeans() {
			return this.nestedTestBeans;
		}
	}


	public static class OptionalCollectionResourceInjectionBean extends ResourceInjectionBean {

		@Autowired(required = false)
		protected ITestBean testBean3;

		private IndexedTestBean indexedTestBean;

		private List<NestedTestBean> nestedTestBeans;

		public List<NestedTestBean> nestedTestBeansSetter;

		@Autowired(required = false)
		public List<NestedTestBean> nestedTestBeansField;

		private ITestBean testBean4;

		@Override
		@Autowired(required = false)
		public void setTestBean2(TestBean testBean2) {
			super.setTestBean2(testBean2);
		}

		@Autowired(required = false)
		private void inject(ITestBean testBean4, List<NestedTestBean> nestedTestBeans, IndexedTestBean indexedTestBean) {
			this.testBean4 = testBean4;
			this.indexedTestBean = indexedTestBean;
			this.nestedTestBeans = nestedTestBeans;
		}

		@Autowired(required = false)
		public void setNestedTestBeans(List<NestedTestBean> nestedTestBeans) {
			this.nestedTestBeansSetter = nestedTestBeans;
		}

		public ITestBean getTestBean3() {
			return this.testBean3;
		}

		public ITestBean getTestBean4() {
			return this.testBean4;
		}

		public IndexedTestBean getIndexedTestBean() {
			return this.indexedTestBean;
		}

		public List<NestedTestBean> getNestedTestBeans() {
			return this.nestedTestBeans;
		}
	}


	public static class ConstructorResourceInjectionBean extends ResourceInjectionBean {

		@Autowired(required = false)
		protected ITestBean testBean3;

		private ITestBean testBean4;

		private NestedTestBean nestedTestBean;

		private ConfigurableListableBeanFactory beanFactory;

		public ConstructorResourceInjectionBean() {
			throw new UnsupportedOperationException();
		}

		public ConstructorResourceInjectionBean(ITestBean testBean3) {
			throw new UnsupportedOperationException();
		}

		@Autowired
		public ConstructorResourceInjectionBean(@Autowired(required = false) ITestBean testBean4,
				@Autowired(required = false) NestedTestBean nestedTestBean,
				ConfigurableListableBeanFactory beanFactory) {
			this.testBean4 = testBean4;
			this.nestedTestBean = nestedTestBean;
			this.beanFactory = beanFactory;
		}

		public ConstructorResourceInjectionBean(NestedTestBean nestedTestBean) {
			throw new UnsupportedOperationException();
		}

		public ConstructorResourceInjectionBean(ITestBean testBean3, ITestBean testBean4, NestedTestBean nestedTestBean) {
			throw new UnsupportedOperationException();
		}

		@Override
		@Autowired(required = false)
		public void setTestBean2(TestBean testBean2) {
			super.setTestBean2(testBean2);
		}

		public ITestBean getTestBean3() {
			return this.testBean3;
		}

		public ITestBean getTestBean4() {
			return this.testBean4;
		}

		public NestedTestBean getNestedTestBean() {
			return this.nestedTestBean;
		}

		public ConfigurableListableBeanFactory getBeanFactory() {
			return this.beanFactory;
		}
	}


	public static class ConstructorsResourceInjectionBean {

		protected ITestBean testBean3;

		private ITestBean testBean4;

		private NestedTestBean[] nestedTestBeans;

		public ConstructorsResourceInjectionBean() {
		}

		@Autowired(required = false)
		public ConstructorsResourceInjectionBean(ITestBean testBean3) {
			this.testBean3 = testBean3;
		}

		@Autowired(required = false)
		public ConstructorsResourceInjectionBean(ITestBean testBean4, NestedTestBean[] nestedTestBeans) {
			this.testBean4 = testBean4;
			this.nestedTestBeans = nestedTestBeans;
		}

		public ConstructorsResourceInjectionBean(NestedTestBean nestedTestBean) {
			throw new UnsupportedOperationException();
		}

		public ConstructorsResourceInjectionBean(ITestBean testBean3, ITestBean testBean4, NestedTestBean nestedTestBean) {
			throw new UnsupportedOperationException();
		}

		public ITestBean getTestBean3() {
			return this.testBean3;
		}

		public ITestBean getTestBean4() {
			return this.testBean4;
		}

		public NestedTestBean[] getNestedTestBeans() {
			return this.nestedTestBeans;
		}
	}


	public static class ConstructorWithoutFallbackBean {

		protected ITestBean testBean3;

		@Autowired(required = false)
		public ConstructorWithoutFallbackBean(ITestBean testBean3) {
			this.testBean3 = testBean3;
		}

		public ITestBean getTestBean3() {
			return this.testBean3;
		}
	}


	public static class ConstructorsCollectionResourceInjectionBean {

		protected ITestBean testBean3;

		private ITestBean testBean4;

		private List<NestedTestBean> nestedTestBeans;

		public ConstructorsCollectionResourceInjectionBean() {
		}

		@Autowired(required = false)
		public ConstructorsCollectionResourceInjectionBean(ITestBean testBean3) {
			this.testBean3 = testBean3;
		}

		@Autowired(required = false)
		public ConstructorsCollectionResourceInjectionBean(ITestBean testBean4, List<NestedTestBean> nestedTestBeans) {
			this.testBean4 = testBean4;
			this.nestedTestBeans = nestedTestBeans;
		}

		public ConstructorsCollectionResourceInjectionBean(NestedTestBean nestedTestBean) {
			throw new UnsupportedOperationException();
		}

		public ConstructorsCollectionResourceInjectionBean(ITestBean testBean3, ITestBean testBean4,
				NestedTestBean nestedTestBean) {
			throw new UnsupportedOperationException();
		}

		public ITestBean getTestBean3() {
			return this.testBean3;
		}

		public ITestBean getTestBean4() {
			return this.testBean4;
		}

		public List<NestedTestBean> getNestedTestBeans() {
			return this.nestedTestBeans;
		}
	}


	public static class SingleConstructorVarargBean {

		private ITestBean testBean;

		private List<NestedTestBean> nestedTestBeans;

		public SingleConstructorVarargBean(ITestBean testBean, NestedTestBean... nestedTestBeans) {
			this.testBean = testBean;
			this.nestedTestBeans = Arrays.asList(nestedTestBeans);
		}

		public ITestBean getTestBean() {
			return this.testBean;
		}

		public List<NestedTestBean> getNestedTestBeans() {
			return this.nestedTestBeans;
		}
	}


	public static class SingleConstructorRequiredCollectionBean {

		private ITestBean testBean;

		private List<NestedTestBean> nestedTestBeans;

		public SingleConstructorRequiredCollectionBean(ITestBean testBean, List<NestedTestBean> nestedTestBeans) {
			this.testBean = testBean;
			this.nestedTestBeans = nestedTestBeans;
		}

		public ITestBean getTestBean() {
			return this.testBean;
		}

		public List<NestedTestBean> getNestedTestBeans() {
			return this.nestedTestBeans;
		}
	}


	public static class SingleConstructorOptionalCollectionBean {

		private ITestBean testBean;

		private List<NestedTestBean> nestedTestBeans;

		public SingleConstructorOptionalCollectionBean(ITestBean testBean,
				@Autowired(required = false) List<NestedTestBean> nestedTestBeans) {
			this.testBean = testBean;
			this.nestedTestBeans = nestedTestBeans;
		}

		public ITestBean getTestBean() {
			return this.testBean;
		}

		public List<NestedTestBean> getNestedTestBeans() {
			return this.nestedTestBeans;
		}
	}


	@SuppressWarnings("serial")
	public static class MyTestBeanMap extends LinkedHashMap<String, TestBean> {
	}


	@SuppressWarnings("serial")
	public static class MyTestBeanSet extends LinkedHashSet<TestBean> {
	}


	public static class MapConstructorInjectionBean {

		private Map<String, TestBean> testBeanMap;

		@Autowired
		public MapConstructorInjectionBean(Map<String, TestBean> testBeanMap) {
			this.testBeanMap = testBeanMap;
		}

		public Map<String, TestBean> getTestBeanMap() {
			return this.testBeanMap;
		}
	}


	public static class QualifiedMapConstructorInjectionBean {

		private Map<String, TestBean> testBeanMap;

		@Autowired
		public QualifiedMapConstructorInjectionBean(@Qualifier("myTestBeanMap") Map<String, TestBean> testBeanMap) {
			this.testBeanMap = testBeanMap;
		}

		public Map<String, TestBean> getTestBeanMap() {
			return this.testBeanMap;
		}
	}


	public static class SetConstructorInjectionBean {

		private Set<TestBean> testBeanSet;

		@Autowired
		public SetConstructorInjectionBean(Set<TestBean> testBeanSet) {
			this.testBeanSet = testBeanSet;
		}

		public Set<TestBean> getTestBeanSet() {
			return this.testBeanSet;
		}
	}


	public static class SelfInjectionBean {

		@Autowired
		public SelfInjectionBean reference;

		@Autowired(required = false)
		public List<SelfInjectionBean> referenceCollection;
	}


	@SuppressWarnings("serial")
	public static class SelfInjectionCollectionBean extends ArrayList<SelfInjectionCollectionBean> {

		@Autowired
		public SelfInjectionCollectionBean reference;

		@Autowired(required = false)
		public List<SelfInjectionCollectionBean> referenceCollection;
	}


	public static class MapFieldInjectionBean {

		@Autowired
		private Map<String, TestBean> testBeanMap;

		public Map<String, TestBean> getTestBeanMap() {
			return this.testBeanMap;
		}
	}


	public static class MapMethodInjectionBean {

		private TestBean testBean;

		private Map<String, TestBean> testBeanMap;

		@Autowired(required = false)
		public void setTestBeanMap(TestBean testBean, Map<String, TestBean> testBeanMap) {
			this.testBean = testBean;
			this.testBeanMap = testBeanMap;
		}

		public TestBean getTestBean() {
			return this.testBean;
		}

		public Map<String, TestBean> getTestBeanMap() {
			return this.testBeanMap;
		}
	}


	@SuppressWarnings("serial")
	public static class ObjectFactoryFieldInjectionBean implements Serializable {

		@Autowired
		private ObjectFactory<TestBean> testBeanFactory;

		public TestBean getTestBean() {
			return this.testBeanFactory.getObject();
		}
	}


	@SuppressWarnings("serial")
	public static class ObjectFactoryConstructorInjectionBean implements Serializable {

		private final ObjectFactory<TestBean> testBeanFactory;

		public ObjectFactoryConstructorInjectionBean(ObjectFactory<TestBean> testBeanFactory) {
			this.testBeanFactory = testBeanFactory;
		}

		public TestBean getTestBean() {
			return this.testBeanFactory.getObject();
		}
	}


	public static class ObjectFactoryQualifierInjectionBean {

		@Autowired
		@Qualifier("testBean")
		private ObjectFactory<?> testBeanFactory;

		public TestBean getTestBean() {
			return (TestBean) this.testBeanFactory.getObject();
		}
	}


	public static class ObjectProviderInjectionBean {

		@Autowired
		private ObjectProvider<TestBean> testBeanProvider;

		private TestBean consumedTestBean;

		public TestBean getTestBean() {
			return this.testBeanProvider.getObject();
		}

		public TestBean getTestBean(String name) {
			return this.testBeanProvider.getObject(name);
		}

		public TestBean getOptionalTestBean() {
			return this.testBeanProvider.getIfAvailable();
		}

		public TestBean getOptionalTestBeanWithDefault() {
			return this.testBeanProvider.getIfAvailable(() -> new TestBean("default"));
		}

		public TestBean consumeOptionalTestBean() {
			this.testBeanProvider.ifAvailable(tb -> consumedTestBean = tb);
			return consumedTestBean;
		}

		public TestBean getUniqueTestBean() {
			return this.testBeanProvider.getIfUnique();
		}

		public TestBean getUniqueTestBeanWithDefault() {
			return this.testBeanProvider.getIfUnique(() -> new TestBean("default"));
		}

		public TestBean consumeUniqueTestBean() {
			this.testBeanProvider.ifUnique(tb -> consumedTestBean = tb);
			return consumedTestBean;
		}

		public List<TestBean> iterateTestBeans() {
			List<TestBean> resolved = new ArrayList<>();
			for (TestBean tb : this.testBeanProvider) {
				resolved.add(tb);
			}
			return resolved;
		}

		public List<TestBean> forEachTestBeans() {
			List<TestBean> resolved = new ArrayList<>();
			this.testBeanProvider.forEach(resolved::add);
			return resolved;
		}

		public List<TestBean> streamTestBeans() {
			return this.testBeanProvider.stream().toList();
		}

		public List<TestBean> sortedTestBeans() {
			return this.testBeanProvider.orderedStream().toList();
		}
	}


	public static class CustomAnnotationRequiredFieldResourceInjectionBean {

		@MyAutowired(optional = false)
		private TestBean testBean;

		public TestBean getTestBean() {
			return this.testBean;
		}
	}


	public static class CustomAnnotationRequiredMethodResourceInjectionBean {

		private TestBean testBean;

		@MyAutowired(optional = false)
		public void setTestBean(TestBean testBean) {
			this.testBean = testBean;
		}

		public TestBean getTestBean() {
			return this.testBean;
		}
	}


	public static class CustomAnnotationOptionalFieldResourceInjectionBean extends ResourceInjectionBean {

		@MyAutowired(optional = true)
		private TestBean testBean3;

		public TestBean getTestBean3() {
			return this.testBean3;
		}
	}


	public static class CustomAnnotationOptionalMethodResourceInjectionBean extends ResourceInjectionBean {

		private TestBean testBean3;

		@MyAutowired(optional = true)
		protected void setTestBean3(TestBean testBean3) {
			this.testBean3 = testBean3;
		}

		public TestBean getTestBean3() {
			return this.testBean3;
		}
	}


	@Target({ElementType.METHOD, ElementType.FIELD})
	@Retention(RetentionPolicy.RUNTIME)
	public @interface MyAutowired {

		boolean optional() default false;
	}


	/**
	 * Bean with a dependency on a {@link FactoryBean}.
	 */
	private static class FactoryBeanDependentBean {

		@Autowired
		private FactoryBean<?> factoryBean;

		public final FactoryBean<?> getFactoryBean() {
			return this.factoryBean;
		}
	}


	public static class StringFactoryBean implements FactoryBean<String> {

		@Override
		public String getObject() throws Exception {
			return "";
		}

		@Override
		public Class<String> getObjectType() {
			return String.class;
		}

		@Override
		public boolean isSingleton() {
			return true;
		}
	}


	public static class OrderedNestedTestBean extends NestedTestBean implements Ordered {

		private int order;

		public void setOrder(int order) {
			this.order = order;
		}

		@Override
		public int getOrder() {
			return this.order;
		}
	}


	@Order(1)
	public static class FixedOrder1NestedTestBean extends NestedTestBean {
	}

	@Order(2)
	public static class FixedOrder2NestedTestBean extends NestedTestBean {
	}


	public interface Repository<T> {
	}

	public static class StringRepository implements Repository<String> {
	}

	public static class IntegerRepository implements Repository<Integer> {
	}

	public static class GenericRepository<T> implements Repository<T> {
	}

	@SuppressWarnings("rawtypes")
	public static class GenericRepositorySubclass extends GenericRepository {
	}

	@SuppressWarnings("rawtypes")
	public static class SimpleRepository implements Repository {
	}

	public static class SimpleRepositorySubclass extends SimpleRepository {
	}


	public static class RepositoryFactoryBean<T> implements FactoryBean<T> {

		@Override
		public T getObject() {
			throw new IllegalStateException();
		}

		@Override
		public Class<?> getObjectType() {
			return Object.class;
		}

		@Override
		public boolean isSingleton() {
			return false;
		}
	}


	public static class RepositoryFieldInjectionBean {

		@Autowired
		public String string;

		@Autowired
		public Integer integer;

		@Autowired
		public String[] stringArray;

		@Autowired
		public Integer[] integerArray;

		@Autowired
		public List<String> stringList;

		@Autowired
		public List<Integer> integerList;

		@Autowired
		public Map<String, String> stringMap;

		@Autowired
		public Map<String, Integer> integerMap;

		@Autowired
		public Repository<String> stringRepository;

		@Autowired
		public Repository<Integer> integerRepository;

		@Autowired
		public Repository<String>[] stringRepositoryArray;

		@Autowired
		public Repository<Integer>[] integerRepositoryArray;

		@Autowired
		public List<Repository<String>> stringRepositoryList;

		@Autowired
		public List<Repository<Integer>> integerRepositoryList;

		@Autowired
		public Map<String, Repository<String>> stringRepositoryMap;

		@Autowired
		public Map<String, Repository<Integer>> integerRepositoryMap;
	}


	public static class RepositoryFieldInjectionBeanWithVariables<S, I> {

		@Autowired
		public S string;

		@Autowired
		public I integer;

		@Autowired
		public S[] stringArray;

		@Autowired
		public I[] integerArray;

		@Autowired
		public List<S> stringList;

		@Autowired
		public List<I> integerList;

		@Autowired
		public Map<String, S> stringMap;

		@Autowired
		public Map<String, I> integerMap;

		@Autowired
		public Repository<S> stringRepository;

		@Autowired
		public Repository<I> integerRepository;

		@Autowired
		public Repository<S>[] stringRepositoryArray;

		@Autowired
		public Repository<I>[] integerRepositoryArray;

		@Autowired
		public List<Repository<S>> stringRepositoryList;

		@Autowired
		public List<Repository<I>> integerRepositoryList;

		@Autowired
		public Map<String, Repository<S>> stringRepositoryMap;

		@Autowired
		public Map<String, Repository<I>> integerRepositoryMap;
	}


	public static class RepositoryFieldInjectionBeanWithSubstitutedVariables
			extends RepositoryFieldInjectionBeanWithVariables<String, Integer> {
	}


	public static class RepositoryFieldInjectionBeanWithQualifiers {

		@Autowired @Qualifier("stringRepo")
		public Repository<?> stringRepository;

		@Autowired @Qualifier("integerRepo")
		public Repository<?> integerRepository;

		@Autowired @Qualifier("stringRepo")
		public Repository<?>[] stringRepositoryArray;

		@Autowired @Qualifier("integerRepo")
		public Repository<?>[] integerRepositoryArray;

		@Autowired @Qualifier("stringRepo")
		public List<Repository<?>> stringRepositoryList;

		@Autowired @Qualifier("integerRepo")
		public List<Repository<?>> integerRepositoryList;

		@Autowired @Qualifier("stringRepo")
		public Map<String, Repository<?>> stringRepositoryMap;

		@Autowired @Qualifier("integerRepo")
		public Map<String, Repository<?>> integerRepositoryMap;
	}


	public static class RepositoryFieldInjectionBeanWithSimpleMatch {

		@Autowired
		public Repository<?> repository;

		@Autowired
		public Repository<String> stringRepository;

		@Autowired
		public Repository<?>[] repositoryArray;

		@Autowired
		public Repository<String>[] stringRepositoryArray;

		@Autowired
		public List<Repository<?>> repositoryList;

		@Autowired
		public List<Repository<String>> stringRepositoryList;

		@Autowired
		public Map<String, Repository<?>> repositoryMap;

		@Autowired
		public Map<String, Repository<String>> stringRepositoryMap;
	}


	public static class RepositoryFactoryBeanInjectionBean {

		@Autowired
		public RepositoryFactoryBean<?> repositoryFactoryBean;
	}


	public static class RepositoryMethodInjectionBean {

		public String string;

		public Integer integer;

		public String[] stringArray;

		public Integer[] integerArray;

		public List<String> stringList;

		public List<Integer> integerList;

		public Map<String, String> stringMap;

		public Map<String, Integer> integerMap;

		public Repository<String> stringRepository;

		public Repository<Integer> integerRepository;

		public Repository<String>[] stringRepositoryArray;

		public Repository<Integer>[] integerRepositoryArray;

		public List<Repository<String>> stringRepositoryList;

		public List<Repository<Integer>> integerRepositoryList;

		public Map<String, Repository<String>> stringRepositoryMap;

		public Map<String, Repository<Integer>> integerRepositoryMap;

		@Autowired
		public void setString(String string) {
			this.string = string;
		}

		@Autowired
		public void setInteger(Integer integer) {
			this.integer = integer;
		}

		@Autowired
		public void setStringArray(String[] stringArray) {
			this.stringArray = stringArray;
		}

		@Autowired
		public void setIntegerArray(Integer[] integerArray) {
			this.integerArray = integerArray;
		}

		@Autowired
		public void setStringList(List<String> stringList) {
			this.stringList = stringList;
		}

		@Autowired
		public void setIntegerList(List<Integer> integerList) {
			this.integerList = integerList;
		}

		@Autowired
		public void setStringMap(Map<String, String> stringMap) {
			this.stringMap = stringMap;
		}

		@Autowired
		public void setIntegerMap(Map<String, Integer> integerMap) {
			this.integerMap = integerMap;
		}

		@Autowired
		public void setStringRepository(Repository<String> stringRepository) {
			this.stringRepository = stringRepository;
		}

		@Autowired
		public void setIntegerRepository(Repository<Integer> integerRepository) {
			this.integerRepository = integerRepository;
		}

		@Autowired
		public void setStringRepositoryArray(Repository<String>[] stringRepositoryArray) {
			this.stringRepositoryArray = stringRepositoryArray;
		}

		@Autowired
		public void setIntegerRepositoryArray(Repository<Integer>[] integerRepositoryArray) {
			this.integerRepositoryArray = integerRepositoryArray;
		}

		@Autowired
		public void setStringRepositoryList(List<Repository<String>> stringRepositoryList) {
			this.stringRepositoryList = stringRepositoryList;
		}

		@Autowired
		public void setIntegerRepositoryList(List<Repository<Integer>> integerRepositoryList) {
			this.integerRepositoryList = integerRepositoryList;
		}

		@Autowired
		public void setStringRepositoryMap(Map<String, Repository<String>> stringRepositoryMap) {
			this.stringRepositoryMap = stringRepositoryMap;
		}

		@Autowired
		public void setIntegerRepositoryMap(Map<String, Repository<Integer>> integerRepositoryMap) {
			this.integerRepositoryMap = integerRepositoryMap;
		}
	}


	public static class RepositoryMethodInjectionBeanWithVariables<S, I> {

		public S string;

		public I integer;

		public S[] stringArray;

		public I[] integerArray;

		public List<S> stringList;

		public List<I> integerList;

		public Map<String, S> stringMap;

		public Map<String, I> integerMap;

		public Repository<S> stringRepository;

		public Repository<I> integerRepository;

		public Repository<S>[] stringRepositoryArray;

		public Repository<I>[] integerRepositoryArray;

		public List<Repository<S>> stringRepositoryList;

		public List<Repository<I>> integerRepositoryList;

		public Map<String, Repository<S>> stringRepositoryMap;

		public Map<String, Repository<I>> integerRepositoryMap;

		@Autowired
		public void setString(S string) {
			this.string = string;
		}

		@Autowired
		public void setInteger(I integer) {
			this.integer = integer;
		}

		@Autowired
		public void setStringArray(S[] stringArray) {
			this.stringArray = stringArray;
		}

		@Autowired
		public void setIntegerArray(I[] integerArray) {
			this.integerArray = integerArray;
		}

		@Autowired
		public void setStringList(List<S> stringList) {
			this.stringList = stringList;
		}

		@Autowired
		public void setIntegerList(List<I> integerList) {
			this.integerList = integerList;
		}

		@Autowired
		public void setStringMap(Map<String, S> stringMap) {
			this.stringMap = stringMap;
		}

		@Autowired
		public void setIntegerMap(Map<String, I> integerMap) {
			this.integerMap = integerMap;
		}

		@Autowired
		public void setStringRepository(Repository<S> stringRepository) {
			this.stringRepository = stringRepository;
		}

		@Autowired
		public void setIntegerRepository(Repository<I> integerRepository) {
			this.integerRepository = integerRepository;
		}

		@Autowired
		public void setStringRepositoryArray(Repository<S>[] stringRepositoryArray) {
			this.stringRepositoryArray = stringRepositoryArray;
		}

		@Autowired
		public void setIntegerRepositoryArray(Repository<I>[] integerRepositoryArray) {
			this.integerRepositoryArray = integerRepositoryArray;
		}

		@Autowired
		public void setStringRepositoryList(List<Repository<S>> stringRepositoryList) {
			this.stringRepositoryList = stringRepositoryList;
		}

		@Autowired
		public void setIntegerRepositoryList(List<Repository<I>> integerRepositoryList) {
			this.integerRepositoryList = integerRepositoryList;
		}

		@Autowired
		public void setStringRepositoryMap(Map<String, Repository<S>> stringRepositoryMap) {
			this.stringRepositoryMap = stringRepositoryMap;
		}

		@Autowired
		public void setIntegerRepositoryMap(Map<String, Repository<I>> integerRepositoryMap) {
			this.integerRepositoryMap = integerRepositoryMap;
		}
	}


	public static class RepositoryMethodInjectionBeanWithSubstitutedVariables
			extends RepositoryMethodInjectionBeanWithVariables<String, Integer> {
	}


	public static class RepositoryConstructorInjectionBean {

		public Repository<String> stringRepository;

		public Repository<Integer> integerRepository;

		public Repository<String>[] stringRepositoryArray;

		public Repository<Integer>[] integerRepositoryArray;

		public List<Repository<String>> stringRepositoryList;

		public List<Repository<Integer>> integerRepositoryList;

		public Map<String, Repository<String>> stringRepositoryMap;

		public Map<String, Repository<Integer>> integerRepositoryMap;

		@Autowired
		public RepositoryConstructorInjectionBean(Repository<String> stringRepository, Repository<Integer> integerRepository,
				Repository<String>[] stringRepositoryArray, Repository<Integer>[] integerRepositoryArray,
				List<Repository<String>> stringRepositoryList, List<Repository<Integer>> integerRepositoryList,
				Map<String, Repository<String>> stringRepositoryMap, Map<String, Repository<Integer>> integerRepositoryMap) {
			this.stringRepository = stringRepository;
			this.integerRepository = integerRepository;
			this.stringRepositoryArray = stringRepositoryArray;
			this.integerRepositoryArray = integerRepositoryArray;
			this.stringRepositoryList = stringRepositoryList;
			this.integerRepositoryList = integerRepositoryList;
			this.stringRepositoryMap = stringRepositoryMap;
			this.integerRepositoryMap = integerRepositoryMap;
		}
	}


	/**
	 * Pseudo-implementation of EasyMock's {@code MocksControl} class.
	 */
	public static class MocksControl {

		@SuppressWarnings("unchecked")
		public <T> T createMock(Class<T> toMock) {
			return (T) Proxy.newProxyInstance(AutowiredAnnotationBeanPostProcessorTests.class.getClassLoader(), new Class<?>[] {toMock},
					(InvocationHandler) (proxy, method, args) -> {
						throw new UnsupportedOperationException("mocked!");
					});
		}
	}


	public interface GenericInterface1<T> {

		String doSomethingGeneric(T o);
	}


	public static class GenericInterface1Impl<T> implements GenericInterface1<T> {

		@Autowired
		private GenericInterface2<T> gi2;

		@Override
		public String doSomethingGeneric(T o) {
			return gi2.doSomethingMoreGeneric(o) + "_somethingGeneric_" + o;
		}

		public static GenericInterface1<String> create() {
			return new StringGenericInterface1Impl();
		}

		public static GenericInterface1<String> createErased() {
			return new GenericInterface1Impl<>();
		}

		@SuppressWarnings("rawtypes")
		public static GenericInterface1 createPlain() {
			return new GenericInterface1Impl();
		}
	}


	public static class StringGenericInterface1Impl extends GenericInterface1Impl<String> {
	}


	public interface GenericInterface2<K> {

		String doSomethingMoreGeneric(K o);
	}


	public static class GenericInterface2Impl implements GenericInterface2<String> {

		@Override
		public String doSomethingMoreGeneric(String o) {
			return "somethingMoreGeneric_" + o;
		}
	}


	public static class ReallyGenericInterface2Impl implements GenericInterface2<Object> {

		@Override
		public String doSomethingMoreGeneric(Object o) {
			return "somethingMoreGeneric_" + o;
		}
	}


	public static class GenericInterface2Bean<K> implements GenericInterface2<K>, BeanNameAware {

		private String name;

		@Override
		public void setBeanName(String name) {
			this.name = name;
		}

		@Override
		public String doSomethingMoreGeneric(K o) {
			return this.name + " " + o;
		}
	}


	public static class MultiGenericFieldInjection {

		@Autowired
		private GenericInterface2<String> stringBean;

		@Autowired
		private GenericInterface2<Integer> integerBean;

		@Override
		public String toString() {
			return this.stringBean.doSomethingMoreGeneric("a") + " " + this.integerBean.doSomethingMoreGeneric(123);
		}
	}


	@SuppressWarnings("rawtypes")
	public static class PlainGenericInterface2Impl implements GenericInterface2 {

		@Override
		public String doSomethingMoreGeneric(Object o) {
			return "somethingMoreGeneric_" + o;
		}
	}


	@SuppressWarnings("rawtypes")
	public interface StockMovement<P extends StockMovementInstruction> {
	}


	@SuppressWarnings("rawtypes")
	public interface StockMovementInstruction<C extends StockMovement> {
	}


	@SuppressWarnings("rawtypes")
	public interface StockMovementDao<S extends StockMovement> {
	}


	@SuppressWarnings("rawtypes")
	public static class StockMovementImpl<P extends StockMovementInstruction> implements StockMovement<P> {
	}


	@SuppressWarnings("rawtypes")
	public static class StockMovementInstructionImpl<C extends StockMovement> implements StockMovementInstruction<C> {
	}


	@SuppressWarnings("rawtypes")
	public static class StockMovementDaoImpl<E extends StockMovement> implements StockMovementDao<E> {
	}


	public static class StockServiceImpl {

		@Autowired
		@SuppressWarnings("rawtypes")
		private StockMovementDao<StockMovement> stockMovementDao;
	}


	public static class MyCallable implements Callable<Thread> {

		@Override
		public Thread call() throws Exception {
			return null;
		}
	}


	public static class SecondCallable implements Callable<Thread>{

		@Override
		public Thread call() throws Exception {
			return null;
		}
	}


	public static abstract class Foo<T extends Runnable, RT extends Callable<T>> {

		private RT obj;

		protected void setObj(RT obj) {
			if (this.obj != null) {
				throw new IllegalStateException("Already called");
			}
			this.obj = obj;
		}
	}


	public static class FooBar extends Foo<Thread, MyCallable> {

		@Override
		@Autowired
		public void setObj(MyCallable obj) {
			super.setObj(obj);
		}
	}


	public static class NullNestedTestBeanFactoryBean implements FactoryBean<NestedTestBean> {

		@Override
		public NestedTestBean getObject() {
			return null;
		}

		@Override
		public Class<?> getObjectType() {
			return NestedTestBean.class;
		}

		@Override
		public boolean isSingleton() {
			return true;
		}
	}


	public static class NullFactoryMethods {

		public static TestBean createTestBean() {
			return null;
		}

		public static NestedTestBean createNestedTestBean() {
			return null;
		}
	}


	public static class ProvidedArgumentBean {

		public ProvidedArgumentBean(String[] args) {
		}
	}


	public static class CollectionFactoryMethods {

		public static Map<String, TestBean> testBeanMap() {
			Map<String, TestBean> tbm = new LinkedHashMap<>();
			tbm.put("testBean1", new TestBean("tb1"));
			tbm.put("testBean2", new TestBean("tb2"));
			return tbm;
		}

		public static Set<TestBean> testBeanSet() {
			Set<TestBean> tbs = new LinkedHashSet<>();
			tbs.add(new TestBean("tb1"));
			tbs.add(new TestBean("tb2"));
			return tbs;
		}
	}


	public static class CustomCollectionFactoryMethods {

		public static CustomMap<String, TestBean> testBeanMap() {
			CustomMap<String, TestBean> tbm = new CustomHashMap<>();
			tbm.put("testBean1", new TestBean("tb1"));
			tbm.put("testBean2", new TestBean("tb2"));
			return tbm;
		}

		public static CustomSet<TestBean> testBeanSet() {
			CustomSet<TestBean> tbs = new CustomHashSet<>();
			tbs.add(new TestBean("tb1"));
			tbs.add(new TestBean("tb2"));
			return tbs;
		}
	}


	public static class CustomMapConstructorInjectionBean {

		private CustomMap<String, TestBean> testBeanMap;

		@Autowired
		public CustomMapConstructorInjectionBean(CustomMap<String, TestBean> testBeanMap) {
			this.testBeanMap = testBeanMap;
		}

		public CustomMap<String, TestBean> getTestBeanMap() {
			return this.testBeanMap;
		}
	}


	public static class CustomSetConstructorInjectionBean {

		private CustomSet<TestBean> testBeanSet;

		@Autowired
		public CustomSetConstructorInjectionBean(CustomSet<TestBean> testBeanSet) {
			this.testBeanSet = testBeanSet;
		}

		public CustomSet<TestBean> getTestBeanSet() {
			return this.testBeanSet;
		}
	}


	public interface CustomMap<K, V> extends Map<K, V> {
	}


	@SuppressWarnings("serial")
	public static class CustomHashMap<K, V> extends LinkedHashMap<K, V> implements CustomMap<K, V> {
	}


	public interface CustomSet<E> extends Set<E> {
	}


	@SuppressWarnings("serial")
	public static class CustomHashSet<E> extends LinkedHashSet<E> implements CustomSet<E> {
	}


	public static class AnnotatedDefaultConstructorBean {

		@Autowired
		public AnnotatedDefaultConstructorBean() {
		}
	}


	public static class SelfInjectingFactoryBean implements FactoryBean<TestBean> {

		private final TestBean exposedTestBean = new TestBean();

		@Autowired
		TestBean testBean;

		@Override
		public TestBean getObject() {
			return exposedTestBean;
		}

		@Override
		public Class<?> getObjectType() {
			return TestBean.class;
		}

		@Override
		public boolean isSingleton() {
			return true;
		}

		public static SelfInjectingFactoryBean create() {
			return new SelfInjectingFactoryBean();
		}
	}


	public static class TestBeanFactory {

		@Order(1)
		public static TestBean newTestBean1() {
			return new TestBean();
		}

		@Order(0)
		public static TestBean newTestBean2() {
			return new TestBean();
		}
	}

}
