/* Copyright (c) 2013 OpenPlans. All rights reserved.
 * This code is licensed under the BSD New License, available at the root
 * application directory.
 */
package org.geogit.metrics;

import static com.google.inject.matcher.Matchers.subclassesOf;

import java.util.Iterator;

import org.geogit.api.AbstractGeoGitOp;
import org.geogit.api.Platform;
import org.geogit.di.GeogitModule;
import org.geogit.di.MethodMatcher;
import org.geogit.storage.ConfigDatabase;
import org.geogit.storage.ObjectDatabase;
import org.geogit.storage.StagingDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;

/**
 * Guice module to be used jointly with {@link GeogitModule}, that logs command ellapsed time to a
 * file.
 * <p>
 * The {@code metrics.enabled} (boolean) <b>local</b> configuration property is used to
 * enable/disable logging tracking of command ellapsed time.
 * <p>
 * The following loggers are used:
 * <ul>
 * <li>{@code org.geogit.metrics.csv}: used to log command times as they happen, with the format
 * {@code <op name:string>, <start time in millis:long>, <ellapsed time in millis:double>, <success:boolean>}
 * <li>{@code org.geogit.metrics.stack}: used to log the stack of command timings whenever a "root"
 * command (that is, one called by client code instead of from another command) finishes. Contains
 * an indented, multiline, stack of commands called with each timing and the relative percent of
 * time each represents to the total time taken by the "root" command. For example:
 * 
 * <pre>
 * <code>
 * LogOp -> 128.31 MILLISECONDS (100.00%), success: true
 *   RevParse -> 114.40 MILLISECONDS (89.16%), success: true
 *       RefParse -> 107.08 MILLISECONDS (83.46%), success: true
 *           ResolveObjectType -> 92.02 MILLISECONDS (71.72%), success: true
 * </code>
 * </pre>
 * 
 * </ul>
 * 
 */
public class MetricsModule extends AbstractModule {

    public static final Logger METRICS_LOGGER = LoggerFactory.getLogger("org.geogit.metrics.csv");

    public static final Logger COMMAND_STACK_LOGGER = LoggerFactory
            .getLogger("org.geogit.metrics.stack");

    @SuppressWarnings("rawtypes")
    @Override
    protected void configure() {

        Provider<Platform> platform = getProvider(Platform.class);
        Provider<ConfigDatabase> configDb = getProvider(ConfigDatabase.class);

        bindInterceptor(subclassesOf(AbstractGeoGitOp.class), new MethodMatcher(
                AbstractGeoGitOp.class, "call"), new GeogitOpMeteredInterceptor(platform, configDb));

        Matcher<Class> objectDatabase = subclassesOf(ObjectDatabase.class);
        Matcher<Class> stagingDatabase = subclassesOf(StagingDatabase.class);

        bindInterceptor(objectDatabase.and(Matchers.not(stagingDatabase)), new MethodMatcher(
                ObjectDatabase.class, "putAll", Iterator.class), new NamedMeteredInterceptor(
                platform, configDb, "ObjectDatabase.putAll"));

        bindInterceptor(stagingDatabase, new MethodMatcher(StagingDatabase.class, "putAll",
                Iterator.class), new NamedMeteredInterceptor(platform, configDb,
                "StagingDatabase.putAll"));
    }
}
