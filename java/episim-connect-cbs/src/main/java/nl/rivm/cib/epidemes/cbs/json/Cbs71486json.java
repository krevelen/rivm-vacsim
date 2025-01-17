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
package nl.rivm.cib.epidemes.cbs.json;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.measure.quantity.Time;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.coala.exception.Thrower;
import io.coala.json.JsonUtil;
import io.coala.math.QuantityUtil;
import io.coala.math.Range;
import io.coala.math.Tuple;
import io.coala.math.WeightedValue;
import io.coala.random.ProbabilityDistribution;
import io.coala.random.QuantityDistribution;
import io.coala.time.TimeUnits;
import io.reactivex.Observable;
import nl.rivm.cib.episim.cbs.RegionPeriod;
import nl.rivm.cib.episim.cbs.TimeUtil;

/**
 * {@link Cbs71486json} helps to import CBS table 71486ned data (JSON
 * formatted). See http://statline.cbs.nl/Statweb/selection/?PA=71486ned and
 * source data at
 * http://opendata.cbs.nl/ODataFeed/odata/71486ned/UntypedDataSet?$format=json
 * (find a tutorial on OpenData operators etc. at
 * http://www.odata.org/getting-started/basic-tutorial/)
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
//@JsonIgnoreProperties( ignoreUnknown = true )
public class Cbs71486json
{
	private static final List<CBSHousehold> HH_TYPES = Arrays
			.stream( CBSHousehold.values() ).filter( c -> !c.aggregate() )
			.collect( Collectors.toList() );

	@JsonIgnore
	public Map<String, Object> props = new HashMap<>();

	@JsonAnySetter
	public void put( final String key, final Object value )
	{
		this.props.put( key, value );
	}

	/** {@code Perioden} as ISO8601 date */
	@JsonProperty( "since" )
	@JsonFormat( shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd" )
	public List<LocalDate> offset;

	/** {@code Perioden} as duration (days) */
	@JsonProperty( "ndays" )
	public List<Integer> dayCounts;

	/** {@code RegioS} */
	@JsonProperty( "reg" )
	public String region;

	/** {@code LeeftijdReferentiepersoon} */
	@JsonProperty( "age" )
	public String ages;

	/** {@code TotaalParticuliereHuishoudens_1} */
	@JsonProperty( "hh" )
	public List<Integer> totals;

	public static class Category extends Tuple
	{

		/**
		 * {@link Category} constructor
		 * 
		 * @param values
		 */
		public Category( final Cbs71486json entry, final int index )
		{
			super( Arrays.asList(

					// key 1
					RegionPeriod.of( entry.region, entry.offset.get( index ) )

					// key 2: age bin
					, Range.of( Integer.parseInt( entry.ages.split( ";" )[0] ),
							Integer.parseInt( entry.ages.split( ";" )[0] ) + 5 )

					// values for distributions in this category
					, Tuple.of( entry.frequenciesFor( index ) ) ) );
		}

		@Override
		public String toString()
		{
			return "cbs71486" + JsonUtil.stringify( this );
		}

		public RegionPeriod regionPeriod()
		{
			return (RegionPeriod) super.values().get( 0 );
		}

		@JsonProperty( "reg" )
		public String regionRef()
		{
			return (String) regionPeriod().regionRef();
		}

		public CBSRegionType regionType()
		{
			return CBSRegionType.parse( regionPeriod().regionRef() );
		}

		@JsonProperty( "per" )
		@JsonFormat( shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM" )
		public LocalDate offset()
		{
			return regionPeriod().periodRef();
		}

		@JsonProperty( "age_range" )
		@SuppressWarnings( "unchecked" )
		public Range<Integer> ageRange()
		{
			return (Range<Integer>) super.values().get( 1 );
		}

		@JsonProperty( "frq" )
		@SuppressWarnings( "rawtypes" )
		public List<? extends Comparable> frequencies()
		{
			return ((Tuple) super.values().get( 2 )).values();
		}

		/** @return reconstructed type/frequency combo's */
		public List<WeightedValue<CBSHousehold>> typeFrequencies()
		{
			@SuppressWarnings( "rawtypes" )
			final List<? extends Comparable> freq = frequencies();
			if( freq.size() != HH_TYPES.size() )
				Thrower.throwNew( IllegalStateException::new,
						() -> "Inconsistent value length: " + freq );
			return IntStream.range( 0, HH_TYPES.size() )
					.mapToObj( i -> WeightedValue.of( HH_TYPES.get( i ),
							(Number) freq.get( i ) ) )
					.collect( Collectors.toList() );
		}

		@JsonIgnore
		private ProbabilityDistribution<CBSHousehold> hhTypeDistCache = null;

		public ProbabilityDistribution<CBSHousehold> hhTypeDist(
			final Function<List<WeightedValue<CBSHousehold>>, ProbabilityDistribution<CBSHousehold>> distFact )
		{
			return this.hhTypeDistCache == null
					? (this.hhTypeDistCache = distFact
							.apply( typeFrequencies() ))
					: this.hhTypeDistCache;
		}

		@JsonIgnore
		private QuantityDistribution<Time> ageDistCache = null;

//		@SuppressWarnings( "unchecked" )
		public QuantityDistribution<Time> ageDist(
			final Function<Range<Integer>, ProbabilityDistribution<? extends Number>> distFact )
		{
			return this.ageDistCache == null
					? (this.ageDistCache = distFact.apply( ageRange() )
							.toQuantities( TimeUnits.YEAR )
							.transform( age -> QuantityUtil.toScale( age, 3 ) ))
					: this.ageDistCache;
		}
	}

//	@SuppressWarnings( "unchecked" )
	public Integer frequenciesFor( final CBSHousehold comp, final int index )
	{
		final List<Integer> freq = frequenciesFor( comp );
		if( freq == null ) return Thrower.throwNew( NullPointerException::new,
				() -> "null values for " + comp + " in " + this );
		if( freq.size() != this.dayCounts.size() )
			return Thrower.throwNew( IllegalStateException::new,
					() -> "Inconsistent values for " + comp + " in " + this );
		return Objects.requireNonNull( freq.get( index ),
				"Got null for " + this.region + ":" + this.ages + ":" + index );
	}

	@SuppressWarnings( "unchecked" )
	public List<Integer> frequenciesFor( final CBSHousehold comp )
	{
		return (List<Integer>) this.props.get( comp.jsonKey() );
	}

	/**
	 * @param index
	 * @return
	 */
	private List<Integer> frequenciesFor( final int index )
	{
		return HH_TYPES.stream().map( c -> frequenciesFor( c, index ) )
				.collect( Collectors.toList() );
	}

	public WeightedValue<Category> toWeightedValue( final int index )
	{
		return WeightedValue.of( new Category( this, index ),
				this.totals.get( index ) );
	}

	@Deprecated
	public Stream<WeightedValue<Category>>
		asFrequencyStream( final Range<LocalDate> offsetRange )
	{
		return TimeUtil.indicesFor( this.offset, offsetRange ).values().stream()
				.map( this::toWeightedValue );
	}

	@Deprecated
	public static Stream<WeightedValue<Category>> readSync(
		final Callable<InputStream> json, final Range<LocalDate> offsetRange )
		throws Exception
	{
		try( final InputStream is = json.call() )
		{
			return Arrays.stream( JsonUtil.valueOf( is, Cbs71486json[].class ) )
					.flatMap( tuple -> tuple.asFrequencyStream( offsetRange ) );
		}
	}

	public Observable<WeightedValue<Category>>
		asFrequencyObservable( final Range<LocalDate> offsetRange )
	{
		return Observable.fromIterable(
				TimeUtil.indicesFor( this.offset, offsetRange ).values() )
				.map( this::toWeightedValue );
	}

	public static Observable<WeightedValue<Category>> readAsync(
		final Callable<InputStream> json, final Range<LocalDate> offsetRange )
	{
		return JsonUtil.readArrayAsync( json, Cbs71486json.class )
				.flatMap( tuple -> tuple.asFrequencyObservable( offsetRange ) );
	}

}