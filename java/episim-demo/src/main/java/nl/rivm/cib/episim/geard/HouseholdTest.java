/* $Id$
 * 
 * Part of ZonMW project no. 50-53000-98-156
 * 
 * @license
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 * Copyright (c) 2016 RIVM National Institute for Health and Environment 
 */
package nl.rivm.cib.episim.geard;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.aeonbits.owner.ConfigCache;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import io.coala.bind.LocalConfig;
import io.coala.dsol3.Dsol3Scheduler;
import io.coala.exception.Thrower;
import io.coala.guice4.Guice4LocalBinder;
import io.coala.log.LogUtil;
import io.coala.math3.Math3ProbabilityDistribution;
import io.coala.math3.Math3PseudoRandom;
import io.coala.random.DistributionParser;
import io.coala.random.ProbabilityDistribution;
import io.coala.random.PseudoRandom;
import io.coala.time.ReplicateConfig;
import io.coala.time.Scheduler;

/**
 * {@link HouseholdTest}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
public class HouseholdTest
{
	/** */
	private static final Logger LOG = LogUtil.getLogger( HouseholdTest.class );

	/**
	 * This test should:
	 */
	@Test
	public void householdCompositionTest() throws InterruptedException
	{
		LOG.trace( "Initializing household composition scenario..." );

		// configure replication 
		ConfigCache.getOrCreate( ReplicateConfig.class, Collections
				.singletonMap( ReplicateConfig.DURATION_KEY, "" + 100 ) );

		// configure tooling
		final LocalConfig config = LocalConfig.builder().withId( "geardSim" )
				.withProvider( Scheduler.class, Dsol3Scheduler.class )
				.withProvider( PseudoRandom.Factory.class,
						Math3PseudoRandom.MersenneTwisterFactory.class )
				.withProvider( ProbabilityDistribution.Factory.class,
						Math3ProbabilityDistribution.Factory.class )
				.withProvider( ProbabilityDistribution.Parser.class,
						DistributionParser.class )
				.build();

		LOG.info( "Starting household test, config: {}", config.toYAML() );
		final Scheduler scheduler = Guice4LocalBinder.of( config )
				.inject( Geard2011Scenario.class ).scheduler();

		final CountDownLatch latch = new CountDownLatch( 1 );
		scheduler.time().subscribe( time ->
		{
			// virtual time passes...
		}, Thrower::rethrowUnchecked, latch::countDown );
		scheduler.resume();
		latch.await( 20, TimeUnit.SECONDS );

		LOG.info( "completed, t={}", scheduler.now() );
	}

}