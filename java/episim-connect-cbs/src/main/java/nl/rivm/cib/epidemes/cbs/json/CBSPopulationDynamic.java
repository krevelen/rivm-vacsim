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

/**
 * {@link CBSPopulationDynamic}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
public enum CBSPopulationDynamic
{
	/** the population count at period start */
	POP( "pop_start", false ),
	/** */
	BIRTHS( "births", false ),
	/** */
	DEATHS( "deaths", false ),
	/** */
	ENTER_MUNICIPALITY( "im_gm", true ),
	/** */
	EXIT_MUNICIPALITY( "em_gm", true ),
	/** */
	IMMIGRATION( "im_nl", true ),
	/** */
	EMIGRATION( "em_nl", true ),
	//
	;

	private final String jsonKey;

	private final boolean unitHousehold;

	private CBSPopulationDynamic( final String jsonKey,
		final boolean unitHousehold )
	{
		this.jsonKey = jsonKey;
		this.unitHousehold = unitHousehold;
	}

	public String jsonKey()
	{
		return this.jsonKey;
	}

	public boolean unitHousehold()
	{
		return this.unitHousehold;
	}
}