package de.metas.handlingunits.allocation.impl;

/*
 * #%L
 * de.metas.handlingunits.base
 * %%
 * Copyright (C) 2015 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */


import org.adempiere.util.Check;
import org.adempiere.util.Services;

import de.metas.handlingunits.allocation.IAllocationRequest;
import de.metas.handlingunits.allocation.IAllocationResult;
import de.metas.handlingunits.allocation.IAllocationStrategy;
import de.metas.handlingunits.allocation.IAllocationStrategyFactory;
import de.metas.handlingunits.model.I_M_HU;
import de.metas.handlingunits.model.I_M_HU_Item;
import de.metas.handlingunits.model.I_M_HU_PI;

public class HUProducerDestination extends AbstractProducerDestination
{
	private final I_M_HU_PI _huPI;

	private boolean _allowCreateNewHU = true;

	protected IAllocationStrategyFactory allocationStrategyFactory = Services.get(IAllocationStrategyFactory.class);

	/**
	 * Maximum number of HUs allowed to be created
	 */
	private int maxHUsToCreate = Integer.MAX_VALUE;

	public HUProducerDestination(final I_M_HU_PI huPI)
	{
		super();

		Check.assumeNotNull(huPI, "huPI not null");
		_huPI = huPI;
	}

	@Override
	protected I_M_HU_PI getM_HU_PI()
	{
		return _huPI;
	}

	@Override
	protected IAllocationResult loadHU(final I_M_HU hu, final IAllocationRequest request)
	{
		final IAllocationStrategy allocationStrategy = allocationStrategyFactory.getAllocationStrategy(hu);
		final IAllocationResult result = allocationStrategy.execute(hu, request);
		return result;
	}

	@Override
	public boolean isAllowCreateNewHU()
	{
		if (!_allowCreateNewHU)
		{
			return false;
		}

		//
		// Check if we already reached the maximum number of HUs that we are allowed to create
		if (getCreatedHUsCount() >= maxHUsToCreate)
		{
			return false;
		}

		return true;
	}

	public void setAllowCreateNewHU(final boolean allowCreateNewHU)
	{
		_allowCreateNewHU = allowCreateNewHU;
	}

	public void setMaxHUsToCreate(final int maxHUsToCreate)
	{
		this.maxHUsToCreate = maxHUsToCreate;
	}

	/**
	 *
	 * @return <code>null</code>.
	 */
	@Override
	protected I_M_HU_Item getParent_HU_Item()
	{
		return null;
	}
}
