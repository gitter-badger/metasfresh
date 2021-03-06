/**
 * 
 */
package de.metas.adempiere.form;

/*
 * #%L
 * de.metas.swat.base
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


import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.Check;
import org.adempiere.util.Services;
import org.adempiere.util.collections.Predicate;
import org.compiere.model.I_C_UOM;
import org.compiere.util.Env;

import de.metas.adempiere.model.I_M_Product;
import de.metas.inoutcandidate.api.IShipmentScheduleBL;
import de.metas.inoutcandidate.model.I_M_ShipmentSchedule;

/**
 * Item to be packed.
 * 
 * Inside contains a mapping of {@link I_M_ShipmentSchedule} to qtys that need to be packed.
 * 
 * @author cg
 * 
 */
public abstract class AbstractPackingItem
{
	private static final int GROUPINGKEY_ToBeGenerated = Integer.MIN_VALUE;

	private final IdentityHashMap<I_M_ShipmentSchedule, BigDecimal> sched2qty;
	private final int groupingKey;
	private final String trxName;
	private I_M_Product product;
	private final I_C_UOM uom;
	private BigDecimal weightSingle;
	private boolean isClosed = false;

	public AbstractPackingItem(final Map<I_M_ShipmentSchedule, BigDecimal> scheds2Qtys, final String trxName)
	{
		this(scheds2Qtys, GROUPINGKEY_ToBeGenerated, trxName);
	}

	public AbstractPackingItem(final Map<I_M_ShipmentSchedule, BigDecimal> scheds2Qtys, final int groupingKey, final String trxName)
	{
		super();

		Check.assumeNotEmpty(scheds2Qtys, "scheds2Qtys not empty");
		this.sched2qty = new IdentityHashMap<I_M_ShipmentSchedule, BigDecimal>(scheds2Qtys);

		this.trxName = trxName;

		final I_M_ShipmentSchedule sched = scheds2Qtys.keySet().iterator().next();
		if (groupingKey == GROUPINGKEY_ToBeGenerated)
		{
			this.groupingKey = computeGroupingKey(sched);
		}
		else
		{
			this.groupingKey = groupingKey;
		}

		this.uom = Services.get(IShipmentScheduleBL.class).getC_UOM(sched);
		Check.assumeNotNull(uom, "uom not null");

		assertValid();
	}

	/**
	 * @return the closed
	 */
	public boolean isClosed()
	{
		return isClosed;
	}

	/**
	 * @param isClosed the isClosed to set
	 */
	public void setClosed(boolean isClosed)
	{
		this.isClosed = isClosed;
	}

	/**
	 * Assets that this packing item is correct.
	 * 
	 * More precisely, checks if schedules have same UOM, same grouping key.
	 */
	protected void assertValid()
	{
		if (sched2qty.isEmpty())
		{
			return;
		}

		I_C_UOM uom = null;
		int groupingKey = GROUPINGKEY_ToBeGenerated;
		boolean firstSched = true;
		for (final I_M_ShipmentSchedule sched : sched2qty.keySet())
		{
			final I_C_UOM currentUOM = Services.get(IShipmentScheduleBL.class).getC_UOM(sched);
			final int currentKey = computeGroupingKey(sched);
			if (firstSched)
			{
				groupingKey = currentKey;
				uom = currentUOM;
				firstSched = false;
			}
			else
			{
				if (uom.getC_UOM_ID() != currentUOM.getC_UOM_ID())
				{
					throw new AdempiereException("schedules does not have same UOM");
				}
				if (groupingKey != currentKey)
				{
					throw new AdempiereException("schedules does not have same grouping key");
				}
			}
		}
	}

	/**
	 * Gets GroupingKey for given shipment schedule
	 * 
	 * NOTE: this method is called from constructor too.
	 * 
	 * @param sched
	 * @return
	 */
	protected int computeGroupingKey(final I_M_ShipmentSchedule sched)
	{
		return Services.get(IShipmentScheduleBL.class).mkKeyForGrouping(sched).hashCode();
	}

	public final Set<I_M_ShipmentSchedule> getShipmentSchedules()
	{
		return new HashSet<I_M_ShipmentSchedule>(sched2qty.keySet());
	}

	public final BigDecimal getQtySum()
	{
		BigDecimal qtySum = BigDecimal.ZERO;

		for (final Entry<I_M_ShipmentSchedule, BigDecimal> e : sched2qty.entrySet())
		{
			final BigDecimal qty = e.getValue();
			qtySum = qtySum.add(qty);
		}

		return qtySum;
	}

	public final void setQtyForSched(final I_M_ShipmentSchedule sched, final BigDecimal qty)
	{
		if (qty == null)
		{
			throw new IllegalArgumentException("Param 'qty' may not be null. Item: " + this);
		}
		if (sched == null)
		{
			throw new IllegalArgumentException("Param 'sched' may not be null. Item: " + this);
		}
		if (!sched2qty.containsKey(sched))
		{
			throw new IllegalArgumentException(sched + " must be added to this item first. Item: " + this);
		}
		sched2qty.put(sched, qty);
	}

	public BigDecimal retrieveWeightSingle(final String trxName)
	{
		if (weightSingle == null)
		{
			weightSingle = retrieveProduct(trxName).getWeight();
		}
		return weightSingle;
	}

	public BigDecimal computeWeight()
	{
		BigDecimal computedWeight = BigDecimal.ZERO;

		for (final I_M_ShipmentSchedule sched : getShipmentSchedules())
		{
			computedWeight = computedWeight.add(retrieveWeightSingle(trxName).multiply(getQtyForSched(sched)));
		}
		return computedWeight;
	}

	public BigDecimal retrieveVolumeSingle(final String trxName)
	{
		return retrieveProduct(trxName).getVolume();
	}

	public I_M_Product retrieveProduct(final String trxName)
	{
		// FIXME: refactor this shit
		if (product == null)
		{
			final int productId = getProductId();
			if (productId > 0)
			{
				final Properties ctx = Env.getCtx();
				product = InterfaceWrapperHelper.create(ctx, productId, I_M_Product.class, trxName);
			}
		}
		return product;
	}

	public I_M_Product getM_Product()
	{
		return retrieveProduct(ITrx.TRXNAME_None);
	}

	public int getProductId()
	{
		final Set<I_M_ShipmentSchedule> shipmentSchedules = getShipmentSchedules();
		if (shipmentSchedules.isEmpty())
		{
			return -1;
		}

		// all scheds must have the same product
		return shipmentSchedules.iterator().next().getM_Product_ID();
	}

	public String getTrxName()
	{
		return trxName;
	}

	public void addSingleSched(final I_M_ShipmentSchedule sched)
	{
		addSchedules(Collections.singletonMap(sched, BigDecimal.ZERO));
	}

	public BigDecimal removeSched(I_M_ShipmentSchedule sched)
	{
		return sched2qty.remove(sched);
	}

	public BigDecimal getQtyForSched(final I_M_ShipmentSchedule sched)
	{
		return sched2qty.get(sched);
	}

	public Map<I_M_ShipmentSchedule, BigDecimal> getQtys()
	{
		return Collections.unmodifiableMap(sched2qty);
	}

	/**
	 * 
	 * @param subtrahent
	 * @return subtracted schedule/qty pairs
	 * @throws PackingItemSubtractException if required qty could not be fully subtracted
	 */
	public Map<I_M_ShipmentSchedule, BigDecimal> subtract(final BigDecimal subtrahent)
	{
		final Predicate<I_M_ShipmentSchedule> acceptShipmentSchedulePredicate = null; // no filter, i.e. accept all
		return subtract(subtrahent, acceptShipmentSchedulePredicate);
	}

	/**
	 * 
	 * @param subtrahent
	 * @param acceptShipmentSchedulePredicate evaluates which shipment schedules shall be considered
	 * @return subtracted schedule/qty pairs
	 * @throws PackingItemSubtractException if required qty could not be fully subtracted (and there were no shipment schedules excluded by the accept predicate)
	 */
	public Map<I_M_ShipmentSchedule, BigDecimal> subtract(final BigDecimal subtrahent, final Predicate<I_M_ShipmentSchedule> acceptShipmentSchedulePredicate)
	{
		final Map<I_M_ShipmentSchedule, BigDecimal> result = new HashMap<I_M_ShipmentSchedule, BigDecimal>();

		//
		// Qty that needs to be subtracted
		BigDecimal qtyToSubtract = subtrahent;
		boolean allowRemainingQtyToSubtract = false;

		//
		// Create a copy of sched2qty and work on it
		// Later, after everything is validated we will copy it back.
		// We are doing this because we want to avoid inconsistencies in case an exception popups
		final IdentityHashMap<I_M_ShipmentSchedule, BigDecimal> sched2qtyCopy = new IdentityHashMap<I_M_ShipmentSchedule, BigDecimal>(sched2qty);

		//
		// Iterate all schedule/qty entries and subtract requested qty
		for (final Iterator<Entry<I_M_ShipmentSchedule, BigDecimal>> it = sched2qtyCopy.entrySet().iterator(); it.hasNext();)
		{
			//
			// If there is no qty to subtract, stop here
			if (qtyToSubtract.signum() <= 0)
			{
				break;
			}

			//
			// Fetch current schedule/qty
			final Entry<I_M_ShipmentSchedule, BigDecimal> schedAndQty = it.next();
			final I_M_ShipmentSchedule sched = schedAndQty.getKey();

			//
			// Make sure current shipment schedule is accepted by our predicate (if any)
			if (acceptShipmentSchedulePredicate != null && !acceptShipmentSchedulePredicate.evaluate(sched))
			{
				// NOTE: we are not removing from map because remaining items will be copied back at the end
				// it.remove();

				// In case we excluded a shipment schedule, we cannot enforce to always have QtyToSubtract=0 at the end.
				// NOTE: in future we could add a parameter or something to enforce this or not.
				// Then pls check which is calling this method, because there is BL which relly on this logic
				// (e.g. Kommissioner Terminal, when we pack the qty which was not found in HUs, but we are doing this only for those shipment schedules which have Force delivery rule)
				allowRemainingQtyToSubtract = true;

				// Skip this record
				continue;
			}

			final BigDecimal schedQty = schedAndQty.getValue();
			final BigDecimal schedQtySubtracted;

			//
			// Current qtyToSubtract is bigger then current schedule's available Qty
			// => subtract only schedule's available Qty
			if (qtyToSubtract.compareTo(schedQty) > 0)
			{
				schedQtySubtracted = schedQty;
				it.remove();
			}
			// Current QtyToSubtract is lower or equal with current schedule's available Qty
			// => subtract the whole qtyToSubtract
			else
			{
				schedQtySubtracted = qtyToSubtract;
				final BigDecimal schedQtyRemaining = schedQty.subtract(schedQtySubtracted);
				schedAndQty.setValue(schedQtyRemaining);
			}

			//
			// Update qtyToSubtract
			qtyToSubtract = qtyToSubtract.subtract(schedQtySubtracted);

			//
			// Add our subtracted schedule/qty pair to result to be returned
			result.put(sched, schedQtySubtracted);
		}

		//
		// If we could not subtract the whole qty that was asked, throw an exception
		if (!allowRemainingQtyToSubtract && qtyToSubtract.signum() != 0)
		{
			throw new PackingItemSubtractException(this, subtrahent, qtyToSubtract);
		}

		//
		// If there were changes (i.e. result is not empty) then we need to copy back our modified sched2qty map
		if (!result.isEmpty())
		{
			sched2qty.clear();
			sched2qty.putAll(sched2qtyCopy);
		}

		//
		// Return the result
		return result;
	}

	public void addSchedules(final Map<I_M_ShipmentSchedule, BigDecimal> toAdd)
	{
		final boolean removeExistingOnes = false;
		addSchedules(toAdd, removeExistingOnes);
	}

	private void addSchedules(final Map<I_M_ShipmentSchedule, BigDecimal> toAdd, final boolean removeExistingOnes)
	{
		//
		// Make sure we are allowed to add those shipment schedules
		for (final I_M_ShipmentSchedule schedToAdd : toAdd.keySet())
		{
			if (!canAddSchedule(schedToAdd))
			{
				throw new IllegalArgumentException(schedToAdd + " can't be added to " + this);
			}
		}

		//
		// Remove existing schedules (if asked)
		// NOTE: we remove existing ones AFTER we validate because "canAddSchedule" always returns true in case there are no schedules
		if (removeExistingOnes)
		{
			sched2qty.clear();
		}

		//
		// Add shipment schedules
		for (final Entry<I_M_ShipmentSchedule, BigDecimal> schedAndQtyToAdd : toAdd.entrySet())
		{
			final I_M_ShipmentSchedule schedToAdd = schedAndQtyToAdd.getKey();
			if (schedToAdd == null)
			{
				continue;
			}
			final BigDecimal qtyToAdd = schedAndQtyToAdd.getValue();
			if (qtyToAdd == null)
			{
				continue;
			}

			final BigDecimal qty = sched2qty.get(schedToAdd);
			if (qty == null)
			{
				// don't invoke addSched because we might have been called by addSched ourselves
				sched2qty.put(schedToAdd, qtyToAdd);
			}
			else
			{
				final BigDecimal qtyNew = qty.add(qtyToAdd);
				sched2qty.put(schedToAdd, qtyNew);
			}
		}
	}

	public void addSchedules(final AbstractPackingItem packingItem)
	{
		final Map<I_M_ShipmentSchedule, BigDecimal> toAdd = packingItem.getQtys();
		addSchedules(toAdd);
	}

	/**
	 * Clears current schedules and set them from given <code>packingItem</code>.
	 * 
	 * @param packingItem
	 */
	public void setSchedules(final AbstractPackingItem packingItem)
	{
		final Map<I_M_ShipmentSchedule, BigDecimal> toAdd = packingItem.getQtys();
		final boolean removeExistingOnes = true;
		addSchedules(toAdd, removeExistingOnes);
	}

	public boolean canAddSchedule(final I_M_ShipmentSchedule schedToAdd)
	{
		if (sched2qty.isEmpty())
		{
			return true;
		}

		return this.groupingKey == computeGroupingKey(schedToAdd);
	}

	public void setWeightSingle(final BigDecimal piWeightSingle)
	{
		this.weightSingle = piWeightSingle;
	}

	public int getGroupingKey()
	{
		return groupingKey;
	}

	public I_C_UOM getC_UOM()
	{
		return uom;
	}
}
